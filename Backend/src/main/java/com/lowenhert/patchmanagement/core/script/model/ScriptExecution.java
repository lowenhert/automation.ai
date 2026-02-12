package com.lowenhert.patchmanagement.core.script.model;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "script_executions")
@Data
public class ScriptExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    private Script script;
    
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;
    
    @Lob
    private String output;
    
    @Lob
    private String error;
    
    @CreationTimestamp
    private LocalDateTime executedAt;
    
    private Long durationMs;
    
    // Add these fields for better tracking
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    
    // Initialize execution (sets initial state)
    public void initializeExecution() {
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }
    
    // Update from ExecutionResult
    public void updateFromResult(ExecutionResult result) {
        this.finishedAt = LocalDateTime.now();
        this.status = result.isSuccess() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED;
        this.output = result.getOutput();
        this.error = result.getError();
        
        // Calculate duration if startedAt was set
        if (this.startedAt != null) {
            this.durationMs = java.time.Duration.between(this.startedAt, this.finishedAt).toMillis();
        } else {
            // Fallback to result's duration if available
            this.durationMs = result.getExecutionTimeMs();
        }
    }
    
    // Helper method for cancellation
    public void markAsCancelled() {
        this.finishedAt = LocalDateTime.now();
        this.status = ExecutionStatus.CANCELLED;
        if (this.startedAt != null) {
            this.durationMs = java.time.Duration.between(this.startedAt, this.finishedAt).toMillis();
        }
    }
}