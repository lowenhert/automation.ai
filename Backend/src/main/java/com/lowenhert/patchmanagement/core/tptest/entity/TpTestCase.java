package com.lowenhert.patchmanagement.core.tptest.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tp_test_case")
public class TpTestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String preconditions;

    @Column(columnDefinition = "TEXT")
    private String steps;

    @Column(name = "expected_result", columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "actual_result", columnDefinition = "TEXT")
    private String actualResult;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column
    private String priority; // LOW, MEDIUM, HIGH, CRITICAL

    @Column
    private String type; // FUNCTIONAL, REGRESSION, SMOKE, PERFORMANCE, OTHER

    @Column(name = "automation_status")
    private String automationStatus; // MANUAL, AUTOMATED, NEEDS_AUTOMATION

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (priority == null) priority = "MEDIUM";
        if (type == null) type = "FUNCTIONAL";
        if (automationStatus == null) automationStatus = "MANUAL";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
