package com.lowenhert.patchmanagement.core.adb;

import com.lowenhert.patchmanagement.core.adb.dto.AdbRequest;
import com.lowenhert.patchmanagement.core.adb.dto.AdbResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/adb")
@RequiredArgsConstructor
public class AdbController {

    private final AdbService adbService;

    @PostMapping("/connect")
    public ResponseEntity<AdbResponse> connect(@RequestBody AdbRequest request) {
        log.info("ADB connect request for IP: {}", request.getIp());
        AdbResponse response = adbService.connect(request.getIp());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/disconnect")
    public ResponseEntity<AdbResponse> disconnect(@RequestBody AdbRequest request) {
        log.info("ADB disconnect request for IP: {}", request.getIp());
        AdbResponse response = adbService.disconnect(request.getIp());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/key")
    public ResponseEntity<AdbResponse> sendKey(@RequestBody AdbRequest request) {
        log.info("ADB key event: {} -> {}", request.getIp(), request.getKeycode());
        AdbResponse response = adbService.sendKeyEvent(request.getIp(), request.getKeycode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/text")
    public ResponseEntity<AdbResponse> sendText(@RequestBody AdbRequest request) {
        log.info("ADB send text to: {}", request.getIp());
        AdbResponse response = adbService.sendText(request.getIp(), request.getText());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/command")
    public ResponseEntity<AdbResponse> runCommand(@RequestBody AdbRequest request) {
        log.info("ADB command to {}: {}", request.getIp(), request.getCommand());
        AdbResponse response = adbService.runCommand(request.getIp(), request.getCommand());
        return ResponseEntity.ok(response);
    }
}
