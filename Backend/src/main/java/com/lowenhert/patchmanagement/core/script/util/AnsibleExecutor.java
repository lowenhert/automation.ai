package com.lowenhert.patchmanagement.core.script.util;

import org.springframework.stereotype.Component;

import com.lowenhert.patchmanagement.core.script.model.ExecutionResult;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.function.Consumer;

@Component
public class AnsibleExecutor implements ScriptExecutor {
    
    @Override
    public ExecutionResult execute(String playbookContent) {
        return execute(playbookContent, null);
    }

    @Override
    public ExecutionResult execute(String playbookContent, Consumer<String> logConsumer) {
        Instant start = Instant.now();
        Path tempFile = null;
        
        try {
            tempFile = Files.createTempFile("ansible-", ".yml");
            Files.write(tempFile, playbookContent.getBytes());
            
            Process process = new ProcessBuilder()
                .command("ansible-playbook", tempFile.toString())
                .redirectErrorStream(true)
                .start();
            
            String output;
            if (logConsumer != null) {
                output = readStreamWithConsumer(process.getInputStream(), logConsumer);
            } else {
                output = readStream(process.getInputStream());
            }
            
            int exitCode = process.waitFor();
            
            return new ExecutionResult()
                .setSuccess(exitCode == 0)
                .setOutput(output)
                .setExecutionTimeMs(Duration.between(start, Instant.now()).toMillis());
                
        } catch (IOException | InterruptedException e) {
            return new ExecutionResult()
                .setSuccess(false)
                .setError("Execution failed: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }
    
    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        }
        return builder.toString();
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