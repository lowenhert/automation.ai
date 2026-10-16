package com.lowenhert.patchmanagement.core.testlibrary;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "script_executions",
        indexes = {
                @Index(name = "idx_script_executions_script", columnList = "script_id"),
                @Index(name = "idx_script_executions_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScriptExecution {

    public enum Status {
        RUNNING, PASSED, FAILED, ERROR, STOPPED, TIMED_OUT, INTERRUPTED;

        public boolean isFinal() {
            return this != RUNNING;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "script_id", nullable = false)
    private Long scriptId;

    @Column(name = "device_serial", nullable = false, length = 128)
    private String deviceSerial;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 16)
    private TestScript.Language language;

    /** Exact content that ran (also kept as a file in the run directory). */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds;

    @Column(name = "exit_code")
    private Integer exitCode;

    @Column(name = "tests_total")
    private Integer testsTotal;

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "tests_failed")
    private Integer testsFailed;

    @Column(name = "tests_skipped")
    private Integer testsSkipped;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "results_json", columnDefinition = "MEDIUMTEXT")
    private String resultsJson;

    @Column(name = "artifacts_json", columnDefinition = "TEXT")
    private String artifactsJson;

    @Column(name = "log_tail", columnDefinition = "MEDIUMTEXT")
    private String logTail;

    @Column(name = "started_by", length = 255)
    private String startedBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
