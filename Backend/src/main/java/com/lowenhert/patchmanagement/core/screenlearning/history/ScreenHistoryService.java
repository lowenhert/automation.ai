package com.lowenhert.patchmanagement.core.screenlearning.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbInputValidator;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenStorage;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenDetailDto;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenHistoryService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MIN_SEARCH_LENGTH = 2;
    static final int MAX_SEARCH_LENGTH = 100;
    private static final int MAX_MATCHES_PER_SCREEN = 5;
    private static final int PREVIEW_LENGTH = 120;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "capturedAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final CapturedScreenRepository repository;
    private final ScreenStorage storage;
    private final ObjectMapper objectMapper;

    /** Persists a successful capture. Reads OCR regions and hashes from the analyzer JSON when available. */
    @Transactional
    public CapturedScreen record(ScreenCaptureResponse capture, AdbDeviceDto device) {
        CapturedScreen screen = CapturedScreen.builder()
                .captureId(capture.getCaptureId())
                .deviceSerial(capture.getDeviceId())
                .deviceModel(device != null ? truncate(device.getModel(), 255) : null)
                .deviceType(device != null ? device.getConnectionType() : null)
                .capturedAt(parseInstant(capture.getTimestamp()))
                .imageFile(capture.getFileName())
                .width(capture.getWidth())
                .height(capture.getHeight())
                .sizeBytes(capture.getSizeBytes())
                .analysisStatus(capture.getAnalysisStatus() != null ? capture.getAnalysisStatus() : "FAILED")
                .ocrStatus(capture.getOcrStatus())
                .analysisError(truncate(capture.getAnalysisError(), 1000))
                .build();

        if ("SUCCESS".equals(screen.getAnalysisStatus())) {
            applyAnalysis(screen);
        }
        if (screen.getSha256() == null) {
            screen.setSha256(sha256(storage.imagePath(screen.getCaptureId())));
        }
        screen.setTextRegionCount(screen.getTextRegions().size());
        return repository.save(screen);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScreenSummaryDto> list(int page, int size, String deviceSerial) {
        Pageable pageable = pageable(page, size);
        Page<CapturedScreen> result = deviceSerial == null || deviceSerial.isBlank()
                ? repository.findAllBy(pageable)
                : repository.findByDeviceSerial(AdbInputValidator.requireSerial(deviceSerial), pageable);
        return PageResponse.of(result, s -> toSummary(s, null));
    }

    @Transactional(readOnly = true)
    public PageResponse<ScreenSummaryDto> search(String text, int page, int size) {
        String query = requireSearchText(text);
        String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
        Page<CapturedScreen> result = repository.searchByText(pattern, pageable(page, size));

        Map<Long, List<String>> matches = new LinkedHashMap<>();
        if (result.hasContent()) {
            List<Long> ids = result.getContent().stream().map(CapturedScreen::getId).toList();
            matches = repository.findMatchingRegions(ids, pattern).stream()
                    .collect(Collectors.groupingBy(r -> r.getScreen().getId(), LinkedHashMap::new,
                            Collectors.mapping(ScreenTextRegion::getText, Collectors.toList())));
        }
        Map<Long, List<String>> finalMatches = matches;
        return PageResponse.of(result, s -> toSummary(s, finalMatches.getOrDefault(s.getId(), List.of()).stream()
                .limit(MAX_MATCHES_PER_SCREEN).toList()));
    }

    @Transactional(readOnly = true)
    public ScreenDetailDto get(String id) {
        String captureId = ScreenStorage.requireCaptureId(id);
        CapturedScreen s = repository.findWithTextRegionsByCaptureId(captureId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + captureId));
        return ScreenDetailDto.builder()
                .id(s.getCaptureId())
                .deviceSerial(s.getDeviceSerial())
                .deviceModel(s.getDeviceModel())
                .deviceType(s.getDeviceType())
                .capturedAt(s.getCapturedAt())
                .width(s.getWidth())
                .height(s.getHeight())
                .resolution(s.getWidth() + "x" + s.getHeight())
                .sizeBytes(s.getSizeBytes())
                .sha256(s.getSha256())
                .phash(s.getPhash())
                .dhash(s.getDhash())
                .ahash(s.getAhash())
                .analysisStatus(s.getAnalysisStatus())
                .ocrStatus(s.getOcrStatus())
                .analysisError(s.getAnalysisError())
                .textRegionCount(s.getTextRegionCount())
                .imageUrl(imageUrl(s.getCaptureId()))
                .textRegions(s.getTextRegions().stream()
                        .map(r -> new ScreenDetailDto.TextRegionDto(r.getRegionIndex(), r.getRegionType(), r.getText(),
                                r.getConfidence(),
                                new ScreenDetailDto.BoundingBox(r.getX(), r.getY(), r.getWidth(), r.getHeight())))
                        .toList())
                .note(ScreenDetailDto.REGION_NOTE)
                .build();
    }

    /** Returns the screenshot file for a stored screen; 404 if the record or the file is missing. */
    @Transactional(readOnly = true)
    public Path imageFile(String id) {
        String captureId = ScreenStorage.requireCaptureId(id);
        CapturedScreen screen = repository.findByCaptureId(captureId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found: " + captureId));
        Path image = storage.imagePath(screen.getCaptureId());
        if (!Files.isRegularFile(image)) {
            log.warn("Screenshot file missing for screen {}: {}", captureId, image);
            throw new ResourceNotFoundException("Screenshot file is missing for screen: " + captureId);
        }
        return image;
    }

    private void applyAnalysis(CapturedScreen screen) {
        Path metadata = storage.metadataPath(screen.getCaptureId());
        JsonNode json;
        try {
            json = objectMapper.readTree(metadata.toFile());
        } catch (IOException e) {
            log.warn("Cannot read analysis metadata {}: {}", metadata, e.getMessage());
            screen.setAnalysisStatus("FAILED");
            screen.setAnalysisError("Analysis metadata could not be read");
            return;
        }
        screen.setMetadataFile(metadata.getFileName().toString());
        screen.setSha256(textOrNull(json.path("checksums").path("sha256")));
        JsonNode hashes = json.path("perceptualHashes");
        screen.setPhash(textOrNull(hashes.path("phash")));
        screen.setDhash(textOrNull(hashes.path("dhash")));
        screen.setAhash(textOrNull(hashes.path("ahash")));

        JsonNode ocr = json.path("ocr");
        screen.setOcrStatus(textOrNull(ocr.path("status")));
        List<String> texts = new ArrayList<>();
        int index = 0;
        for (JsonNode r : ocr.path("textRegions")) {
            String text = r.path("text").asText("").strip();
            JsonNode b = r.path("bbox");
            if (text.isEmpty() || !b.isObject()) continue;
            screen.addTextRegion(ScreenTextRegion.builder()
                    .regionIndex(++index)
                    .regionType(ScreenTextRegion.TYPE_OCR_TEXT)
                    .text(text)
                    .confidence(Math.max(0, Math.min(100, r.path("confidence").asDouble(0))))
                    .x(Math.max(0, b.path("x").asInt()))
                    .y(Math.max(0, b.path("y").asInt()))
                    .width(Math.max(0, b.path("width").asInt()))
                    .height(Math.max(0, b.path("height").asInt()))
                    .build());
            texts.add(text);
        }
        screen.setExtractedText(texts.isEmpty() ? null : truncate(String.join("\n", texts), CapturedScreen.MAX_EXTRACTED_TEXT));
    }

    private static ScreenSummaryDto toSummary(CapturedScreen s, List<String> matchedTexts) {
        return ScreenSummaryDto.builder()
                .id(s.getCaptureId())
                .deviceSerial(s.getDeviceSerial())
                .deviceModel(s.getDeviceModel())
                .deviceType(s.getDeviceType())
                .capturedAt(s.getCapturedAt())
                .width(s.getWidth())
                .height(s.getHeight())
                .resolution(s.getWidth() + "x" + s.getHeight())
                .phash(s.getPhash())
                .analysisStatus(s.getAnalysisStatus())
                .ocrStatus(s.getOcrStatus())
                .textRegionCount(s.getTextRegionCount())
                .textPreview(preview(s.getExtractedText()))
                .imageUrl(imageUrl(s.getCaptureId()))
                .matchedTexts(matchedTexts)
                .build();
    }

    static Pageable pageable(int page, int size) {
        if (page < 0) throw new BadRequestException("page must be >= 0");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        return PageRequest.of(page, size, NEWEST_FIRST);
    }

    static String requireSearchText(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.length() < MIN_SEARCH_LENGTH || trimmed.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Search text must be between " + MIN_SEARCH_LENGTH + " and " + MAX_SEARCH_LENGTH + " characters");
        }
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw new BadRequestException("Search text contains invalid characters");
        }
        return trimmed;
    }

    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static String imageUrl(String captureId) {
        return "/api/screens/" + captureId + "/image";
    }

    private static String preview(String text) {
        if (text == null) return null;
        String oneLine = text.replace('\n', ' ').replaceAll("\\s+", " ").strip();
        return oneLine.length() > PREVIEW_LENGTH ? oneLine.substring(0, PREVIEW_LENGTH) + "…" : oneLine;
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null ? Instant.now() : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("Could not hash {}: {}", file, e.getMessage());
            return null;
        }
    }
}
