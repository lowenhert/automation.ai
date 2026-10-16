package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Runs the configured adb executable with a fixed argument list. No shell is involved,
 * so arguments are never interpreted by a command interpreter on the host.
 */
@Slf4j
@Component
public class AdbCommandExecutor {

    private final ProcessRunner processRunner;
    private final String adbPath;
    private final long timeoutMs;

    public AdbCommandExecutor(ProcessRunner processRunner,
                              @Value("${app.adb.path:adb}") String adbPath,
                              @Value("${app.adb.timeout-ms:15000}") long timeoutMs) {
        if (adbPath == null || adbPath.isBlank()) {
            throw new IllegalStateException("app.adb.path must not be blank");
        }
        this.processRunner = processRunner;
        this.adbPath = adbPath.trim();
        this.timeoutMs = timeoutMs;
    }

    public String getAdbPath() {
        return adbPath;
    }

    public AdbCommandResult execute(List<String> args) {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(adbPath);
        command.addAll(args);

        log.info("Executing: adb {}", String.join(" ", args));

        try {
            ProcessRunner.ProcessResult result = processRunner.run(command, null, timeoutMs);
            String output = (result.stdout() + "\n" + result.stderr()).trim();
            log.debug("adb exit={} output={}", result.exitCode(), output);
            return new AdbCommandResult(result.exitCode(), output);
        } catch (IOException e) {
            log.error("Unable to start adb at '{}': {}", adbPath, e.getMessage());
            throw new ServiceUnavailableException("ADB executable not available. Check the 'app.adb.path' configuration.");
        } catch (TimeoutException e) {
            log.warn("adb {} timed out after {} ms", String.join(" ", args), timeoutMs);
            throw new ServiceUnavailableException("ADB command timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("ADB command was interrupted");
        }
    }
}
