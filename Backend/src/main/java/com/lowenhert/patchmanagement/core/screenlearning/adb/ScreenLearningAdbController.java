package com.lowenhert.patchmanagement.core.screenlearning.adb;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbActionResponse;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbConnectRequest;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDisconnectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/screen-learning/adb")
@RequiredArgsConstructor
public class ScreenLearningAdbController {

    private final ScreenLearningAdbService adbService;

    @GetMapping("/devices")
    public ResponseEntity<List<AdbDeviceDto>> listDevices() {
        return ResponseEntity.ok(adbService.listDevices());
    }

    @GetMapping("/devices/info")
    public ResponseEntity<AdbDeviceDto> getDevice(@RequestParam("serial") String serial) {
        return ResponseEntity.ok(adbService.getDevice(serial));
    }

    @PostMapping("/connect")
    public ResponseEntity<AdbActionResponse> connect(@RequestBody AdbConnectRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        log.info("Screen Learning ADB connect: {}:{}", request.getHost(), request.getPort());
        return ResponseEntity.ok(adbService.connect(request.getHost(), request.getPort()));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<AdbActionResponse> disconnect(@RequestBody AdbDisconnectRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        log.info("Screen Learning ADB disconnect: {}", request.getSerial());
        return ResponseEntity.ok(adbService.disconnect(request.getSerial()));
    }
}
