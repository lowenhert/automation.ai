package com.lowenhert.patchmanagement.core.tptest.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tp_test_run")
public class TpTestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "suite_id", nullable = false)
    private Long suiteId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(nullable = false)
    private String name;

    @Column
    private String status; // ACTIVE, COMPLETED, CLOSED

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column
    private LocalDate deadline;

    @Column(name = "test_type")
    private String testType;

    @Column(name = "stb_box_name")
    private String stbBoxName;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "ACTIVE";
    }
}
