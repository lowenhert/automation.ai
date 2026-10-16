package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An identified screen (e.g. "Settings › Network"), grouping all captures that match the same fingerprint.
 * Label and risk may come from OCR heuristics or from the LLM; LLM output can only make actions more restricted.
 */
@Entity
@Table(name = "screen_nodes",
        indexes = {
                @Index(name = "idx_screen_nodes_device", columnList = "device_serial"),
                @Index(name = "idx_screen_nodes_phash", columnList = "phash")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenNode {

    public enum RiskLevel { SAFE, CAUTION, DESTRUCTIVE }

    public enum LabelSource { OCR, LLM }

    /** How the LLM interpreted the screen. IMAGE only when the image was sent and its content verified against OCR. */
    public enum InterpretationInput { NONE, TEXT, IMAGE, IMAGE_UNVERIFIED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_serial", nullable = false, length = 128)
    private String deviceSerial;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "screen_type", length = 32)
    private String screenType;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 16)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "label_source", nullable = false, length = 8)
    private LabelSource labelSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "interpretation_input", nullable = false, length = 20)
    private InterpretationInput interpretationInput;

    @Column(name = "interpretation_model", length = 120)
    private String interpretationModel;

    @Column(name = "interpretation_error", length = 500)
    private String interpretationError;

    @Column(name = "phash", length = 32)
    private String phash;

    @Column(name = "text_signature", columnDefinition = "TEXT")
    private String textSignature;

    @Column(name = "representative_capture_id", nullable = false, length = 36)
    private String representativeCaptureId;

    @Column(name = "visit_count", nullable = false)
    private Integer visitCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (firstSeenAt == null) firstSeenAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
        if (visitCount == null) visitCount = 1;
        if (riskLevel == null) riskLevel = RiskLevel.SAFE;
        if (labelSource == null) labelSource = LabelSource.OCR;
        if (interpretationInput == null) interpretationInput = InterpretationInput.NONE;
    }
}
