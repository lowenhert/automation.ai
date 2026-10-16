package com.lowenhert.patchmanagement.core.screenlearning.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenStorage;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenDetailDto;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenSummaryDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScreenHistoryServiceTest {

    @Autowired
    private CapturedScreenRepository repository;
    @Autowired
    private EntityManager entityManager;

    @TempDir
    Path captureDir;

    private ScreenHistoryService service;

    @BeforeEach
    void setUp() {
        service = new ScreenHistoryService(repository, new ScreenStorage(captureDir.toString()), new ObjectMapper());
    }

    private String store(String deviceSerial, Instant capturedAt, String... regionTexts) throws Exception {
        String id = UUID.randomUUID().toString();
        Files.write(captureDir.resolve(id + ".png"), new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        StringBuilder regions = new StringBuilder();
        for (int i = 0; i < regionTexts.length; i++) {
            if (i > 0) regions.append(',');
            regions.append("{\"id\":").append(i + 1).append(",\"type\":\"OCR_TEXT\",\"text\":\"").append(regionTexts[i])
                    .append("\",\"confidence\":").append(90 + i)
                    .append(",\"bbox\":{\"x\":").append(10 * i).append(",\"y\":").append(100 * i)
                    .append(",\"width\":200,\"height\":40}}");
        }
        Files.writeString(captureDir.resolve(id + ".json"), """
                {"checksums":{"sha256":"%s"},
                 "perceptualHashes":{"phash":"c5ffd59b90a02ae0","dhash":"8a000338c01396f2","ahash":"fffee00000007a7e"},
                 "ocr":{"status":"SUCCESS","textRegions":[%s]}}
                """.formatted("a".repeat(64), regions));

        service.record(ScreenCaptureResponse.builder()
                .captureId(id).deviceId(deviceSerial).fileName(id + ".png")
                .timestamp(capturedAt.toString()).width(1080).height(2340).sizeBytes(7)
                .analysisStatus("SUCCESS").ocrStatus("SUCCESS").textRegionCount(regionTexts.length)
                .build(),
                AdbDeviceDto.builder().serial(deviceSerial).model("sdk gphone64").connectionType("EMULATOR").build());
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    @Test
    void recordsCaptureWithRegionsHashesAndDevice() throws Exception {
        String id = store("emulator-5554", Instant.parse("2026-10-15T07:00:00Z"), "Network internet", "Bluetooth, pairing");

        ScreenDetailDto detail = service.get(id);

        assertEquals(id, detail.getId());
        assertEquals("emulator-5554", detail.getDeviceSerial());
        assertEquals("sdk gphone64", detail.getDeviceModel());
        assertEquals("EMULATOR", detail.getDeviceType());
        assertEquals("1080x2340", detail.getResolution());
        assertEquals("c5ffd59b90a02ae0", detail.getPhash());
        assertEquals("a".repeat(64), detail.getSha256());
        assertEquals("SUCCESS", detail.getAnalysisStatus());
        assertEquals(2, detail.getTextRegionCount());
        assertEquals("/api/screens/" + id + "/image", detail.getImageUrl());
        assertTrue(detail.getNote().contains("not verified"));

        ScreenDetailDto.TextRegionDto second = detail.getTextRegions().get(1);
        assertEquals(2, second.index());
        assertEquals("OCR_TEXT", second.type());
        assertEquals("Bluetooth, pairing", second.text());
        assertEquals(new ScreenDetailDto.BoundingBox(10, 100, 200, 40), second.bbox());

        CapturedScreen row = repository.findByCaptureId(id).orElseThrow();
        assertEquals(id + ".png", row.getImageFile());
        assertEquals(id + ".json", row.getMetadataFile());
    }

    @Test
    void failedAnalysisIsStoredWithJavaComputedChecksum() throws Exception {
        String id = UUID.randomUUID().toString();
        Files.write(captureDir.resolve(id + ".png"), new byte[]{1, 2, 3});
        service.record(ScreenCaptureResponse.builder()
                .captureId(id).deviceId("192.168.1.20:5555").fileName(id + ".png").timestamp("2026-10-15T07:00:00Z")
                .width(1920).height(1080).analysisStatus("FAILED").analysisError("Analyzer dependencies missing").build(), null);

        ScreenDetailDto detail = service.get(id);
        assertEquals("FAILED", detail.getAnalysisStatus());
        assertEquals("Analyzer dependencies missing", detail.getAnalysisError());
        assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81", detail.getSha256());
        assertTrue(detail.getTextRegions().isEmpty());
    }

    @Test
    void listIsPaginatedNewestFirstAndFilterableByDevice() throws Exception {
        String oldest = store("emulator-5554", Instant.parse("2026-10-15T07:00:00Z"), "One");
        String middle = store("192.168.1.20:5555", Instant.parse("2026-10-15T08:00:00Z"), "Two");
        String newest = store("emulator-5554", Instant.parse("2026-10-15T09:00:00Z"), "Three");

        PageResponse<ScreenSummaryDto> first = service.list(0, 2, null);
        assertEquals(List.of(newest, middle), first.content().stream().map(ScreenSummaryDto::getId).toList());
        assertEquals(3, first.totalElements());
        assertEquals(2, first.totalPages());
        assertEquals(List.of(oldest), service.list(1, 2, null).content().stream().map(ScreenSummaryDto::getId).toList());

        PageResponse<ScreenSummaryDto> emulator = service.list(0, 10, "emulator-5554");
        assertEquals(List.of(newest, oldest), emulator.content().stream().map(ScreenSummaryDto::getId).toList());
        assertEquals("Three", emulator.content().get(0).getTextPreview());
        assertNull(emulator.content().get(0).getMatchedTexts());
    }

    @Test
    void searchMatchesCaseInsensitivelyAndReturnsMatchedRegions() throws Exception {
        String settings = store("emulator-5554", Instant.parse("2026-10-15T07:00:00Z"), "Network internet", "Bluetooth, pairing", "Apps");
        store("emulator-5554", Instant.parse("2026-10-15T08:00:00Z"), "Thu, Oct 15", "12:49");

        PageResponse<ScreenSummaryDto> result = service.search("NETWORK", 0, 20);

        assertEquals(1, result.totalElements());
        assertEquals(settings, result.content().get(0).getId());
        assertEquals(List.of("Network internet"), result.content().get(0).getMatchedTexts());
    }

    @Test
    void searchEscapesLikeWildcards() throws Exception {
        store("emulator-5554", Instant.parse("2026-10-15T07:00:00Z"), "Battery 100% charged");
        store("emulator-5554", Instant.parse("2026-10-15T08:00:00Z"), "Battery 1000 mAh");

        assertEquals(1, service.search("100%", 0, 20).totalElements());
        assertEquals(0, service.search("a_b", 0, 20).totalElements());
        assertEquals(0, service.search("%%", 0, 20).totalElements());
    }

    @Test
    void validationErrors() {
        assertThrows(BadRequestException.class, () -> service.list(-1, 20, null));
        assertThrows(BadRequestException.class, () -> service.list(0, 0, null));
        assertThrows(BadRequestException.class, () -> service.list(0, 101, null));
        assertThrows(BadRequestException.class, () -> service.list(0, 20, "-s"));
        assertThrows(BadRequestException.class, () -> service.search("a", 0, 20));
        assertThrows(BadRequestException.class, () -> service.search("x".repeat(101), 0, 20));
        assertThrows(BadRequestException.class, () -> service.search("ab\u0000c", 0, 20));
        assertThrows(BadRequestException.class, () -> service.get("../etc/passwd"));
        assertThrows(BadRequestException.class, () -> service.imageFile("not-a-uuid"));
    }

    @Test
    void notFoundErrors() throws Exception {
        String unknown = UUID.randomUUID().toString();
        assertThrows(ResourceNotFoundException.class, () -> service.get(unknown));
        assertThrows(ResourceNotFoundException.class, () -> service.imageFile(unknown));

        String id = store("emulator-5554", Instant.now(), "Hello");
        assertEquals(captureDir.resolve(id + ".png"), service.imageFile(id));
        Files.delete(captureDir.resolve(id + ".png"));
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.imageFile(id));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void escapeLike() {
        assertEquals("50!%!_off!!\\", ScreenHistoryService.escapeLike("50%_off!\\"));
    }
}
