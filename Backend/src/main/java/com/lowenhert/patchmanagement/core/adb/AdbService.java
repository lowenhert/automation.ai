package com.lowenhert.patchmanagement.core.adb;

import com.lowenhert.patchmanagement.core.adb.dto.AdbResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AdbService {

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d{1,5})?$"
    );

    private static final Pattern KEYCODE_PATTERN = Pattern.compile(
            "^KEYCODE_[A-Z0-9_]+$"
    );

    private static final Pattern SHELL_METACHARACTERS = Pattern.compile("[;&|`$<>()\\\\\\r\\n]");

    public AdbResponse connect(String ip) {
        if (!isValidIp(ip)) {
            return AdbResponse.builder().success(false).error("Invalid IP address").build();
        }

        String target = ip.contains(":") ? ip : ip + ":5555";
        String result = executeAdbCommand("connect", target);

        if (result == null) {
            return AdbResponse.builder().success(false).error("Failed to execute ADB command").build();
        }

        boolean success = result.contains("connected") || result.contains("already connected");
        return AdbResponse.builder()
                .success(success)
                .output(result)
                .error(success ? null : result)
                .build();
    }

    public AdbResponse disconnect(String ip) {
        if (!isValidIp(ip)) {
            return AdbResponse.builder().success(false).error("Invalid IP address").build();
        }

        String target = ip.contains(":") ? ip : ip + ":5555";
        String result = executeAdbCommand("disconnect", target);

        return AdbResponse.builder()
                .success(true)
                .output(result)
                .build();
    }

    public AdbResponse sendKeyEvent(String ip, String keycode) {
        if (!isValidIp(ip)) {
            return AdbResponse.builder().success(false).error("Invalid IP address").build();
        }
        if (!KEYCODE_PATTERN.matcher(keycode).matches()) {
            return AdbResponse.builder().success(false).error("Invalid keycode").build();
        }

        String target = ip.contains(":") ? ip : ip + ":5555";
        String result = executeAdbCommand("-s", target, "shell", "input", "keyevent", keycode);

        return AdbResponse.builder()
                .success(result != null)
                .output(result)
                .error(result == null ? "Failed to send key event" : null)
                .build();
    }

    public AdbResponse sendText(String ip, String text) {
        if (!isValidIp(ip)) {
            return AdbResponse.builder().success(false).error("Invalid IP address").build();
        }
        if (text == null || text.isEmpty()) {
            return AdbResponse.builder().success(false).error("Text cannot be empty").build();
        }

        // Sanitize text - only allow alphanumeric and basic characters
        String sanitized = text.replaceAll("[^a-zA-Z0-9 ._\\-]", "");

        String target = ip.contains(":") ? ip : ip + ":5555";
        String result = executeAdbCommand("-s", target, "shell", "input", "text", sanitized);

        return AdbResponse.builder()
                .success(result != null)
                .output(result)
                .error(result == null ? "Failed to send text" : null)
                .build();
    }

    public AdbResponse runCommand(String ip, String command) {
        if (!isValidIp(ip)) {
            return AdbResponse.builder().success(false).error("Invalid IP address").build();
        }

        // Only allow safe read-only commands
        if (!isSafeCommand(command)) {
            return AdbResponse.builder().success(false).error("Command not allowed").build();
        }

        String target = ip.contains(":") ? ip : ip + ":5555";
        String result = executeAdbCommand("-s", target, "shell", command);

        return AdbResponse.builder()
                .success(result != null)
                .output(result)
                .error(result == null ? "Failed to execute command" : null)
                .build();
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return IP_PATTERN.matcher(ip.trim()).matches();
    }

    private boolean isSafeCommand(String command) {
        if (command == null || command.isBlank()) return false;
        // The command runs in the device shell, so block chaining, substitution and redirection
        if (SHELL_METACHARACTERS.matcher(command).find()) return false;
        // Whitelist safe commands - prevent injection
        String cmd = command.trim().toLowerCase();
        return cmd.startsWith("getprop") ||
               cmd.startsWith("dumpsys") ||
               cmd.startsWith("pm list") ||
               cmd.startsWith("input ") ||
               cmd.startsWith("am start") ||
               cmd.startsWith("settings get");
    }

    private String executeAdbCommand(String... args) {
        try {
            String[] command = new String[args.length + 1];
            command[0] = "adb";
            System.arraycopy(args, 0, command, 1, args.length);

            log.info("Executing ADB command: adb {}", String.join(" ", args));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ADB command timed out");
                return null;
            }

            String result = output.toString().trim();
            log.info("ADB command result: {}", result);
            return result;

        } catch (Exception e) {
            log.error("Error executing ADB command: {}", e.getMessage(), e);
            return null;
        }
    }
}
