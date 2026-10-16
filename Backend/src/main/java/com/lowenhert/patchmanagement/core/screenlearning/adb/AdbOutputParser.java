package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AdbOutputParser {

    private static final Pattern GETPROP_LINE = Pattern.compile("^\\[([^\\]]+)]:\\s*\\[(.*)]$");
    private static final Pattern WM_SIZE = Pattern.compile("(Physical|Override) size:\\s*(\\d+x\\d+)");

    private AdbOutputParser() {
    }

    /** Parses the output of {@code adb devices -l}. */
    static List<AdbDeviceDto> parseDevices(String output) {
        List<AdbDeviceDto> devices = new ArrayList<>();
        if (output == null) return devices;

        for (String raw : output.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("List of devices")) continue;

            String[] tokens = line.split("\\s+");
            if (tokens.length < 2) continue;

            String serial = tokens[0];
            int idx = 1;
            String state = tokens[idx++];
            if ("no".equals(state) && idx < tokens.length && tokens[idx].startsWith("permissions")) {
                state = "no permissions";
                idx++;
            }

            AdbDeviceDto.AdbDeviceDtoBuilder builder = AdbDeviceDto.builder()
                    .serial(serial)
                    .state(state)
                    .connectionType(connectionType(serial));

            for (int i = idx; i < tokens.length; i++) {
                int sep = tokens[i].indexOf(':');
                if (sep <= 0) continue;
                String key = tokens[i].substring(0, sep);
                String value = tokens[i].substring(sep + 1);
                switch (key) {
                    case "model" -> builder.model(value.replace('_', ' '));
                    case "product" -> builder.product(value);
                    case "device" -> builder.deviceName(value);
                    case "transport_id" -> builder.transportId(value);
                    default -> { }
                }
            }
            devices.add(builder.build());
        }
        return devices;
    }

    /** Parses the full output of {@code adb shell getprop}. */
    static Map<String, String> parseGetprop(String output) {
        Map<String, String> props = new HashMap<>();
        if (output == null) return props;
        for (String line : output.split("\\R")) {
            Matcher m = GETPROP_LINE.matcher(line.trim());
            if (m.matches()) {
                props.put(m.group(1), m.group(2));
            }
        }
        return props;
    }

    /** Parses {@code adb shell wm size}; an override size wins over the physical size. */
    static String parseWmSize(String output) {
        if (output == null) return null;
        String physical = null;
        String override = null;
        Matcher m = WM_SIZE.matcher(output);
        while (m.find()) {
            if ("Override".equals(m.group(1))) override = m.group(2);
            else physical = m.group(2);
        }
        return override != null ? override : physical;
    }

    static String connectionType(String serial) {
        if (serial.startsWith("emulator-")) return "EMULATOR";
        if (serial.contains(":") || serial.contains("._adb-tls-connect.")) return "NETWORK";
        return "USB";
    }
}
