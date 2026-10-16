package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;

import java.util.regex.Pattern;

public final class AdbInputValidator {

    static final int DEFAULT_PORT = 5555;

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern NUMERIC_HOST = Pattern.compile("^[\\d.]+$");
    private static final Pattern HOSTNAME = Pattern.compile(
            "^(?=.{1,253}$)[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    // First char must not be '-' so a serial can never be read as an adb option
    private static final Pattern SERIAL = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:\\-]{0,127}$");

    private AdbInputValidator() {
    }

    static String requireHost(String host) {
        if (host == null || host.isBlank()) {
            throw new BadRequestException("Host is required");
        }
        String trimmed = host.trim();
        boolean numeric = NUMERIC_HOST.matcher(trimmed).matches();
        boolean valid = numeric ? IPV4.matcher(trimmed).matches() : HOSTNAME.matcher(trimmed).matches();
        if (!valid) {
            throw new BadRequestException("Invalid host. Use an IPv4 address or hostname");
        }
        return trimmed;
    }

    static int requirePort(Integer port) {
        if (port == null) return DEFAULT_PORT;
        if (port < 1 || port > 65535) {
            throw new BadRequestException("Port must be between 1 and 65535");
        }
        return port;
    }

    public static String requireSerial(String serial) {
        if (serial == null || serial.isBlank()) {
            throw new BadRequestException("Device serial is required");
        }
        String trimmed = serial.trim();
        if (!SERIAL.matcher(trimmed).matches()) {
            throw new BadRequestException("Invalid device serial");
        }
        return trimmed;
    }

    /** Validates a network serial of the form host:port, as used by adb connect/disconnect. */
    static String requireNetworkSerial(String serial) {
        String trimmed = requireSerial(serial);
        int sep = trimmed.lastIndexOf(':');
        if (sep <= 0 || sep == trimmed.length() - 1) {
            throw new BadRequestException("Only network devices (host:port) can be disconnected");
        }
        String host = requireHost(trimmed.substring(0, sep));
        int port;
        try {
            port = Integer.parseInt(trimmed.substring(sep + 1));
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid port in device serial");
        }
        return host + ":" + requirePort(port);
    }
}
