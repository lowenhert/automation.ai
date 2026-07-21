package com.lowenhert.patchmanagement.core.stbexecutions;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stb_execution_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StbExecutionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "test_case_id")
    private Long testCaseId;

    @Column(name = "module_name")
    private String moduleName;

    @Column(name = "step_number")
    private Integer stepNumber;

    @Column(name = "step_name")
    private String stepName;

    @Column(name = "status", nullable = false)
    private String status; // PASSED, FAILED, SKIPPED, RUNNING

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "screenshot_path")
    private String screenshotPath;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}
