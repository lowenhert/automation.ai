package com.lowenhert.patchmanagement.core.script.util;

import java.util.function.Consumer;

import com.lowenhert.patchmanagement.core.script.model.ExecutionResult;

public interface ScriptExecutor {
    ExecutionResult execute(String content) throws Exception;
	ExecutionResult execute(String scriptContent, Consumer<String> logConsumer);
}