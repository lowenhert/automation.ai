package com.lowenhert.patchmanagement.core.script.model;

public enum ExecutionStatus {
    PENDING,     // Execution is queued
    RUNNING,     // Execution is currently running
    COMPLETED,   // Execution finished successfully
    FAILED,      // Execution failed
    CANCELLED,  // Execution was cancelled
    TIMED_OUT    // Execution exceeded time limit
}