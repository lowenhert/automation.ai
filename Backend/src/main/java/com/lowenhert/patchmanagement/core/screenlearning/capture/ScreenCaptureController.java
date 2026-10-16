package com.lowenhert.patchmanagement.core.screenlearning.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureRequest;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/screens")
@RequiredArgsConstructor
public class ScreenCaptureController {

    private final ScreenCaptureService captureService;

    @PostMapping("/capture")
    public ResponseEntity<ScreenCaptureResponse> capture(@RequestBody ScreenCaptureRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        log.info("Screen capture request for device: {}", request.getDeviceId());
        return ResponseEntity.ok(captureService.capture(request.getDeviceId()));
    }

    @GetMapping("/{captureId}/analysis")
    public ResponseEntity<JsonNode> analysis(@PathVariable String captureId) {
        return ResponseEntity.ok(captureService.getAnalysis(captureId));
    }
}
