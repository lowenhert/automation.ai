package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.exploration.ExplorationDtos.*;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exploration")
@RequiredArgsConstructor
public class ExplorationController {

    private final ExplorationService service;

    @PostMapping("/sessions")
    public ResponseEntity<SessionDto> start(@RequestBody StartRequest request, Authentication auth) {
        return ResponseEntity.ok(service.start(request, auth != null ? auth.getName() : null));
    }

    @GetMapping("/sessions")
    public ResponseEntity<PageResponse<SessionDto>> list(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(page, size));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/sessions/{id}/actions")
    public ResponseEntity<StepDto> act(@PathVariable Long id, @RequestBody ActionRequest request) {
        return ResponseEntity.ok(service.act(id, request == null ? null : request.action()));
    }

    @PostMapping("/sessions/{id}/stop")
    public ResponseEntity<SessionDto> stop(@PathVariable Long id) {
        return ResponseEntity.ok(service.stop(id));
    }

    @GetMapping("/sessions/{id}/steps")
    public ResponseEntity<PageResponse<StepDto>> steps(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.steps(id, page, size));
    }

    @GetMapping("/graph")
    public ResponseEntity<GraphDto> graph(@RequestParam String deviceId) {
        return ResponseEntity.ok(service.graph(deviceId));
    }

    @GetMapping("/nodes/{id}")
    public ResponseEntity<NodeDto> node(@PathVariable Long id) {
        return ResponseEntity.ok(service.node(id));
    }
}
