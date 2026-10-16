package com.lowenhert.patchmanagement.core.screenlearning.history;

import jakarta.persistence.*;
import lombok.*;

/** Text found by OCR on a captured screen. Not a verified interactive/clickable element. */
@Entity
@Table(name = "screen_text_regions",
        indexes = @Index(name = "idx_screen_text_regions_screen", columnList = "screen_capture_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenTextRegion {

    public static final String TYPE_OCR_TEXT = "OCR_TEXT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_capture_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_screen_text_regions_screen"))
    private CapturedScreen screen;

    @Column(name = "region_index", nullable = false)
    private Integer regionIndex;

    @Column(name = "region_type", nullable = false, length = 32)
    private String regionType;

    @Column(name = "region_text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "bbox_x", nullable = false)
    private Integer x;

    @Column(name = "bbox_y", nullable = false)
    private Integer y;

    @Column(name = "bbox_width", nullable = false)
    private Integer width;

    @Column(name = "bbox_height", nullable = false)
    private Integer height;
}
