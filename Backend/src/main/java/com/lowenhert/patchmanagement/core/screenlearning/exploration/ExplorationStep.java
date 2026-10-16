package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** One recorded action within a session (transition history). */
@Entity
@Table(name = "exploration_steps",
        indexes = @Index(name = "idx_exploration_steps_session", columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExplorationStep {

    public enum Status { OK, BLOCKED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private RemoteKey action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "from_node_id")
    private Long fromNodeId;

    @Column(name = "to_node_id")
    private Long toNodeId;

    @Column(name = "from_capture_id", length = 36)
    private String fromCaptureId;

    @Column(name = "to_capture_id", length = 36)
    private String toCaptureId;

    @Column(name = "new_screen", nullable = false)
    private boolean newScreen;

    @Column(name = "same_screen", nullable = false)
    private boolean sameScreen;

    @Column(name = "auto_triggered", nullable = false)
    private boolean autoTriggered;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
