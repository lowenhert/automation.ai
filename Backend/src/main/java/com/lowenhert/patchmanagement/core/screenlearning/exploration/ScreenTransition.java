package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Aggregated graph edge: source screen → action → destination screen, with how often it was observed. */
@Entity
@Table(name = "screen_transitions",
        uniqueConstraints = @UniqueConstraint(name = "uk_screen_transitions_edge",
                columnNames = {"from_node_id", "action", "to_node_id"}),
        indexes = @Index(name = "idx_screen_transitions_device", columnList = "device_serial"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreenTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_serial", nullable = false, length = 128)
    private String deviceSerial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_node_id", nullable = false, foreignKey = @ForeignKey(name = "fk_screen_transitions_from"))
    private ScreenNode fromNode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private RemoteKey action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_node_id", nullable = false, foreignKey = @ForeignKey(name = "fk_screen_transitions_to"))
    private ScreenNode toNode;

    @Column(name = "observation_count", nullable = false)
    private Integer observationCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
