package com.lowenhert.patchmanagement.core.script.model;

import lombok.Data;

@Data
public class ExecutionResult {
    private boolean success;
    private String output;
    private String error;
    private long executionTimeMs;
    
    // Fluent setters for chaining
    public ExecutionResult setSuccess(boolean success) {
        this.success = success;
        return this;
    }
    
    public ExecutionResult setOutput(String output) {
        this.output = output;
        return this;
    }
    
    public ExecutionResult setError(String error) {
        this.error = error;
        return this;
    }
    
    public ExecutionResult setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
        return this;
    }
}