package com.lowenhert.patchmanagement.core.stbexecutions;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stb_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StbExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_name", nullable = false)
    private String executionName;

    @Column(name = "stb_no", nullable = false)
    private Integer stbNo;

    @Column(name = "box_name")
    private String boxName;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED

    @Column(name = "total_tests")
    private Integer totalTests;

    @Column(name = "passed")
    private Integer passed;

    @Column(name = "failed")
    private Integer failed;

    @Column(name = "skipped")
    private Integer skipped;

    @Column(name = "test_case_ids", columnDefinition = "TEXT")
    private String testCaseIds; // comma-separated IDs

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (totalTests == null) totalTests = 0;
        if (passed == null) passed = 0;
        if (failed == null) failed = 0;
        if (skipped == null) skipped = 0;
    }
}
