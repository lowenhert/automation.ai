package com.lowenhert.patchmanagement.core.screenlearning.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs an external program from an explicit argument list (never through a shell),
 * with a hard timeout and bounded output capture.
 */
@Slf4j
@Component
public class ProcessRunner {

    private static final int MAX_OUTPUT_CHARS = 512 * 1024;
    // Child daemons (e.g. the adb server) may inherit the pipes, so don't wait for EOF forever
    private static final long OUTPUT_DRAIN_MS = 1000;

    public ProcessResult run(List<String> command, File workingDir, long timeoutMs)
            throws IOException, TimeoutException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) pb.directory(workingDir);
        Process process = pb.start();

        StringBuffer stdout = new StringBuffer();
        StringBuffer stderr = new StringBuffer();
        Thread outReader = startReader(process.getInputStream(), stdout, "proc-stdout");
        Thread errReader = startReader(process.getErrorStream(), stderr, "proc-stderr");

        try {
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new TimeoutException("Process timed out after " + timeoutMs + " ms");
            }
            outReader.join(OUTPUT_DRAIN_MS);
            errReader.join(OUTPUT_DRAIN_MS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }

        return new ProcessResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
    }

    private static Thread startReader(InputStream in, StringBuffer target, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (target.length() < MAX_OUTPUT_CHARS) target.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // stream closed when the process is destroyed
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
