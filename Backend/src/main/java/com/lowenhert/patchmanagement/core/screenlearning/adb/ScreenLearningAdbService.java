package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbActionResponse;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenLearningAdbService {

    private static final String ONLINE = "device";
    private static final int MAX_MESSAGE_LENGTH = 300;

    private final AdbCommandExecutor executor;

    public List<AdbDeviceDto> listDevices() {
        List<AdbDeviceDto> devices = queryDevices();
        for (AdbDeviceDto device : devices) {
            if (ONLINE.equals(device.getState())) {
                try {
                    enrich(device);
                } catch (RuntimeException e) {
                    log.warn("Could not read info for {}: {}", device.getSerial(), e.getMessage());
                    device.setInfoError(e.getMessage());
                }
            }
        }
        return devices;
    }

    public AdbDeviceDto getDevice(String serial) {
        String validSerial = AdbInputValidator.requireSerial(serial);
        AdbDeviceDto device = queryDevices().stream()
                .filter(d -> d.getSerial().equals(validSerial))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + validSerial));
        if (!ONLINE.equals(device.getState())) {
            throw new BadRequestException("Device is " + device.getState() + "; device info is unavailable");
        }
        enrich(device);
        return device;
    }

    /** Validates the serial and ensures it is currently attached and online; returns the device as listed by adb. */
    public AdbDeviceDto requireOnlineDevice(String serial) {
        String validSerial = AdbInputValidator.requireSerial(serial);
        AdbDeviceDto device = queryDevices().stream()
                .filter(d -> d.getSerial().equals(validSerial))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Device not found: " + validSerial));
        if (!ONLINE.equals(device.getState())) {
            throw new BadRequestException("Device is " + device.getState() + "; it must be online");
        }
        return device;
    }

    /** Sends one allow-listed key event; the serial must already be validated (see requireOnlineDevice). */
    public void sendKey(String serial, RemoteKey key) {
        String validSerial = AdbInputValidator.requireSerial(serial);
        AdbCommandResult result = executor.execute(List.of("-s", validSerial, "shell", "input", "keyevent", key.keycode()));
        if (!result.isSuccess()) {
            throw new ServiceUnavailableException("Failed to send " + key + ": " + summarize(result.output()));
        }
    }

    public AdbActionResponse connect(String host, Integer port) {
        String target = AdbInputValidator.requireHost(host) + ":" + AdbInputValidator.requirePort(port);
        AdbCommandResult result = executor.execute(List.of("connect", target));
        String output = result.output().toLowerCase(Locale.ROOT);

        // adb connect often exits 0 even on failure, so the output must be checked as well
        boolean connected = result.isSuccess()
                && output.contains("connected to")
                && !output.contains("failed")
                && !output.contains("cannot");

        if (!connected) {
            throw new BadRequestException("Connection failed: " + summarize(result.output()));
        }
        return AdbActionResponse.builder()
                .success(true)
                .serial(target)
                .message(summarize(result.output()))
                .build();
    }

    public AdbActionResponse disconnect(String serial) {
        String target = AdbInputValidator.requireNetworkSerial(serial);
        AdbCommandResult result = executor.execute(List.of("disconnect", target));

        if (!result.isSuccess() || !result.output().toLowerCase(Locale.ROOT).contains("disconnected")) {
            throw new BadRequestException("Disconnect failed: " + summarize(result.output()));
        }
        return AdbActionResponse.builder()
                .success(true)
                .serial(target)
                .message(summarize(result.output()))
                .build();
    }

    private List<AdbDeviceDto> queryDevices() {
        AdbCommandResult result = executor.execute(List.of("devices", "-l"));
        if (!result.isSuccess()) {
            throw new ServiceUnavailableException("Failed to list ADB devices: " + summarize(result.output()));
        }
        return AdbOutputParser.parseDevices(result.output());
    }

    private void enrich(AdbDeviceDto device) {
        String serial = device.getSerial();

        AdbCommandResult props = executor.execute(List.of("-s", serial, "shell", "getprop"));
        if (!props.isSuccess()) {
            throw new ServiceUnavailableException("Failed to read device properties: " + summarize(props.output()));
        }
        Map<String, String> p = AdbOutputParser.parseGetprop(props.output());
        device.setManufacturer(blankToNull(p.get("ro.product.manufacturer")));
        String model = blankToNull(p.get("ro.product.model"));
        if (model != null) device.setModel(model);
        device.setAndroidVersion(blankToNull(p.get("ro.build.version.release")));
        device.setSdkVersion(blankToNull(p.get("ro.build.version.sdk")));

        AdbCommandResult size = executor.execute(List.of("-s", serial, "shell", "wm", "size"));
        if (size.isSuccess()) {
            device.setResolution(AdbOutputParser.parseWmSize(size.output()));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String summarize(String output) {
        if (output == null || output.isBlank()) return "no output from adb";
        String firstLine = output.strip().lines().findFirst().orElse("");
        return firstLine.length() > MAX_MESSAGE_LENGTH ? firstLine.substring(0, MAX_MESSAGE_LENGTH) : firstLine;
    }
}
