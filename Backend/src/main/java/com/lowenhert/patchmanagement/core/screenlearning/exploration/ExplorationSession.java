package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "exploration_sessions",
        indexes = @Index(name = "idx_exploration_sessions_device", columnList = "device_serial"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExplorationSession {

    public enum Mode { MANUAL, AUTO }

    public enum Status {
        ACTIVE, RUNNING, STOPPED, COMPLETED, LIMIT_REACHED, STALLED, FAILED, INTERRUPTED;

        public boolean isOpen() {
            return this == ACTIVE || this == RUNNING;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_serial", nullable = false, length = 128)
    private String deviceSerial;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "max_steps", nullable = false)
    private Integer maxSteps;

    @Column(name = "max_duration_seconds", nullable = false)
    private Integer maxDurationSeconds;

    @Column(name = "settle_ms", nullable = false)
    private Integer settleMs;

    @Column(name = "allow_select", nullable = false)
    private boolean allowSelect;

    @Column(name = "allow_home", nullable = false)
    private boolean allowHome;

    @Column(name = "steps_taken", nullable = false)
    private Integer stepsTaken;

    @Column(name = "new_screens", nullable = false)
    private Integer newScreens;

    @Column(name = "start_node_id")
    private Long startNodeId;

    @Column(name = "current_node_id")
    private Long currentNodeId;

    @Column(name = "current_capture_id", length = 36)
    private String currentCaptureId;

    @Column(name = "stop_reason", length = 500)
    private String stopReason;

    @Column(name = "started_by", length = 255)
    private String startedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
        if (stepsTaken == null) stepsTaken = 0;
        if (newScreens == null) newScreens = 0;
    }
}
