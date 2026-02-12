package com.lowenhert.patchmanagement.core.script.util;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

import com.lowenhert.patchmanagement.core.script.model.ExecutionResult;

@Component
public class PowerShellExecutor implements ScriptExecutor {
    
    @Override
    public ExecutionResult execute(String scriptContent) {
        return execute(scriptContent, null);
    }

    @Override
    public ExecutionResult execute(String scriptContent, Consumer<String> logConsumer) {
        Instant start = Instant.now();
        
        try {
            Process process = new ProcessBuilder()
                .command("pwsh", "-Command", scriptContent)
                .redirectErrorStream(true)
                .start();
            
            String output;
            if (logConsumer != null) {
                output = readStreamWithConsumer(process.getInputStream(), logConsumer);
            } else {
                output = readStream(process.getInputStream());
            }
            
            int exitCode = process.waitFor();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            
            return new ExecutionResult()
                .setSuccess(exitCode == 0)
                .setOutput(output)
                .setExecutionTimeMs(durationMs);
                
        } catch (IOException | InterruptedException e) {
            return new ExecutionResult()
                .setSuccess(false)
                .setError("PowerShell execution failed: " + e.getMessage());
        }
    }
    
    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
    
    private String readStreamWithConsumer(InputStream inputStream, Consumer<String> logConsumer) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
                logConsumer.accept(line);
            }
        }
        return builder.toString();
    }
}