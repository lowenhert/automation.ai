package com.lowenhert.patchmanagement.core.script.service;

import java.io.OutputStream;

import com.lowenhert.patchmanagement.core.script.dto.ScriptExecutionResponseDTO;

public interface ScriptExecutorService {
    ScriptExecutionResponseDTO executeScript(Long scriptId);
    ScriptExecutionResponseDTO getExecutionStatus(Long executionId);
    void cancelExecution(Long executionId);
	void executeScriptStream(Long scriptId, OutputStream outputStream);
}