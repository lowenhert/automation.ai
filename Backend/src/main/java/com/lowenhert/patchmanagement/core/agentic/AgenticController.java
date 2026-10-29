package com.lowenhert.patchmanagement.core.agentic;

import com.lowenhert.patchmanagement.core.agentic.AgenticDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agentic")
@RequiredArgsConstructor
public class AgenticController {

    private final AgenticService agenticService;

    @PostMapping("/run")
    public ResponseEntity<AgenticRunResult> run(@RequestBody AgenticRunRequest request, Authentication auth) {
        String username = auth != null ? auth.getName() : "agentic";
        return ResponseEntity.ok(agenticService.run(request, username));
    }

    @GetMapping("/{executionId}/metrics")
    public ResponseEntity<AgenticMetrics> getMetrics(@PathVariable Long executionId) {
        return ResponseEntity.ok(agenticService.getMetrics(executionId));
    }

    @PostMapping("/re-execute")
    public ResponseEntity<AgenticRunResult> reExecute(@RequestBody AgenticReExecuteRequest request, Authentication auth) {
        String username = auth != null ? auth.getName() : "agentic";
        return ResponseEntity.ok(agenticService.reExecute(request, username));
    }
}
