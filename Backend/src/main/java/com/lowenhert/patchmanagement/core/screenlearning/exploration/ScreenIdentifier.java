package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;

/** Maps a capture to an existing screen node (duplicate detection) or creates a new node. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenIdentifier {

    static final int MAX_LABEL = 60;

    private final ScreenNodeRepository nodeRepository;
    private final DestructiveActionGuard guard;

    public record Identification(ScreenNode node, boolean isNew, double score) {
    }

    @Transactional
    public Identification identify(String deviceSerial, CapturedScreen screen) {
        ScreenFingerprint fingerprint = ScreenFingerprint.of(screen);

        ScreenNode best = null;
        double bestScore = Double.MAX_VALUE;
        for (ScreenNode node : nodeRepository.findByDeviceSerial(deviceSerial)) {
            double score = fingerprint.matchScore(ScreenFingerprint.of(node));
            if (score >= 0 && score < bestScore) {
                best = node;
                bestScore = score;
            }
        }

        if (best != null) {
            best.setVisitCount(best.getVisitCount() + 1);
            best.setLastSeenAt(Instant.now());
            log.debug("Capture {} matched screen node {} (score {})", screen.getCaptureId(), best.getId(), bestScore);
            return new Identification(nodeRepository.save(best), false, bestScore);
        }

        ScreenNode created = nodeRepository.save(ScreenNode.builder()
                .deviceSerial(deviceSerial)
                .label(heuristicLabel(screen))
                .riskLevel(guard.assess(screen.getExtractedText()))
                .labelSource(ScreenNode.LabelSource.OCR)
                .interpretationInput(ScreenNode.InterpretationInput.NONE)
                .phash(screen.getPhash())
                .textSignature(fingerprint.signature())
                .representativeCaptureId(screen.getCaptureId())
                .build());
        log.info("New screen node {} '{}' from capture {}", created.getId(), created.getLabel(), screen.getCaptureId());
        return new Identification(created, true, 0);
    }

    /** First prominent OCR line below the status bar; falls back to a generic name. */
    static String heuristicLabel(CapturedScreen screen) {
        int statusBarLimit = (int) Math.round(screen.getHeight() * ScreenFingerprint.STATUS_BAR_FRACTION);
        return screen.getTextRegions().stream()
                .filter(r -> r.getY() >= statusBarLimit)
                .filter(r -> r.getText().strip().length() >= 3 && r.getText().chars().anyMatch(Character::isLetter))
                .filter(r -> r.getConfidence() == null || r.getConfidence() >= 70)
                .sorted(Comparator.comparing(ScreenTextRegion::getY))
                .map(r -> r.getText().strip())
                .findFirst()
                .map(t -> t.length() > MAX_LABEL ? t.substring(0, MAX_LABEL) : t)
                .orElse("Screen " + screen.getCaptureId().substring(0, 8));
    }
}
