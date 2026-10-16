package com.lowenhert.patchmanagement.core.screenlearning.history;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A captured screen. The PNG and analyzer JSON live on disk; this row stores only their file names. */
@Entity
@Table(name = "screen_captures",
        uniqueConstraints = @UniqueConstraint(name = "uk_screen_captures_capture_id", columnNames = "capture_id"),
        indexes = {
                @Index(name = "idx_screen_captures_device", columnList = "device_serial"),
                @Index(name = "idx_screen_captures_captured_at", columnList = "captured_at"),
                @Index(name = "idx_screen_captures_phash", columnList = "phash")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CapturedScreen {

    public static final int MAX_EXTRACTED_TEXT = 60_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capture_id", nullable = false, length = 36)
    private String captureId;

    @Column(name = "device_serial", nullable = false, length = 128)
    private String deviceSerial;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "device_type", length = 16)
    private String deviceType; // EMULATOR, NETWORK, USB

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "image_file", nullable = false, length = 64)
    private String imageFile;

    @Column(name = "metadata_file", length = 64)
    private String metadataFile;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "phash", length = 32)
    private String phash;

    @Column(name = "dhash", length = 32)
    private String dhash;

    @Column(name = "ahash", length = 32)
    private String ahash;

    @Column(name = "analysis_status", nullable = false, length = 16)
    private String analysisStatus; // SUCCESS, FAILED

    @Column(name = "ocr_status", length = 16)
    private String ocrStatus; // SUCCESS, FAILED

    @Column(name = "analysis_error", length = 1000)
    private String analysisError;

    @Column(name = "text_region_count", nullable = false)
    private Integer textRegionCount;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "screen", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("regionIndex ASC")
    @Builder.Default
    private List<ScreenTextRegion> textRegions = new ArrayList<>();

    public void addTextRegion(ScreenTextRegion region) {
        region.setScreen(this);
        textRegions.add(region);
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (textRegionCount == null) textRegionCount = textRegions.size();
    }
}
