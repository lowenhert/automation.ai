package com.lowenhert.patchmanagement.core.script.util;

import org.springframework.stereotype.Component;

import com.lowenhert.patchmanagement.core.script.model.ExecutionResult;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.function.Consumer;

@Component
public class PythonExecutor implements ScriptExecutor {
    
    @Override
    public ExecutionResult execute(String scriptContent) {
        return execute(scriptContent, null);
    }

    @Override
    public ExecutionResult execute(String scriptContent, Consumer<String> logConsumer) {
        Instant start = Instant.now();
        Path tempFile = null;
        
        try {
            // 1. Create temp Python file
            tempFile = Files.createTempFile("python-", ".py");
            Files.write(tempFile, scriptContent.getBytes());
            
            // 2. Execute script
            String pythonCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "py" : "python3";
            Process process = new ProcessBuilder()
                .command(pythonCmd, tempFile.toString())
                .redirectErrorStream(true)
                .start();

            
            // 3. Capture output
            String output;
            if (logConsumer != null) {
                output = readStreamWithConsumer(process.getInputStream(), logConsumer);
            } else {
                output = readStream(process.getInputStream());
            }
            
            int exitCode = process.waitFor();
            
            // 4. Return result
            return new ExecutionResult()
                .setSuccess(exitCode == 0)
                .setOutput(output)
                .setExecutionTimeMs(Duration.between(start, Instant.now()).toMillis());
                
        } catch (IOException | InterruptedException e) {
            return new ExecutionResult()
                .setSuccess(false)
                .setError("Python execution failed: " + e.getMessage());
        } finally {
            // 5. Clean up temp file
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