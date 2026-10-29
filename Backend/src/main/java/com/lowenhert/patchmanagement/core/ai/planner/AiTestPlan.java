package com.lowenhert.patchmanagement.core.ai.planner;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A generated test plan. Plans can be executed directly or scheduled. */
@Entity
@Table(name = "ai_test_plans",
        indexes = {
                @Index(name = "idx_ai_test_plans_device", columnList = "device_serial"),
                @Index(name = "idx_ai_test_plans_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTestPlan {

    public enum Status { DRAFT, APPROVED, REJECTED, EXECUTING, EXECUTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "goal", nullable = false, columnDefinition = "TEXT")
    private String goal;

    @Column(name = "device_serial", nullable = false, length = 128)
    private String deviceSerial;

    @Column(name = "start_screen_id")
    private Long startScreenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "step_count", nullable = false)
    private Integer stepCount;

    @Column(name = "max_steps", nullable = false)
    private Integer maxSteps;

    /** Canonical, validated plan (re-serialised from the typed model, not the raw LLM text). */
    @Column(name = "plan_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String planJson;

    /** Flags and per-step evidence computed by the server. */
    @Column(name = "review_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String reviewJson;

    /** Which screens/transitions the plan was generated from (audit trail). */
    @Column(name = "knowledge_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String knowledgeJson;

    @Column(name = "error_flags", nullable = false)
    private Integer errorFlags;

    @Column(name = "warning_flags", nullable = false)
    private Integer warningFlags;

    @Column(name = "model", length = 120)
    private String model;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.DRAFT;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
