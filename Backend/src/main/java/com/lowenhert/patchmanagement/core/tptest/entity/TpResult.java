package com.lowenhert.patchmanagement.core.tptest.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tp_result")
public class TpResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_case_id", nullable = false)
    private Long runCaseId;

    @Column(nullable = false)
    private String status; // PASSED, FAILED, BLOCKED, RETEST

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "executed_by")
    private String executedBy;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "defect_link")
    private String defectLink;

    @PrePersist
    protected void onCreate() {
        executedAt = LocalDateTime.now();
    }
}
