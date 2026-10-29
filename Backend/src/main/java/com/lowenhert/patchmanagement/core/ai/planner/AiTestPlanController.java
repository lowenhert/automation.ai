package com.lowenhert.patchmanagement.core.ai.planner;

import com.lowenhert.patchmanagement.core.ai.planner.TestPlannerDtos.*;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/test-plans")
@RequiredArgsConstructor
public class AiTestPlanController {

    private final AiTestPlannerService planner;

    @PostMapping
    public ResponseEntity<PlanDetail> generate(@RequestBody GenerateRequest request, Authentication auth) {
        return ResponseEntity.ok(planner.generate(request, auth != null ? auth.getName() : null));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PlanSummary>> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          @RequestParam(required = false) String deviceId,
                                                          @RequestParam(required = false) AiTestPlan.Status status) {
        return ResponseEntity.ok(planner.list(page, size, deviceId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanDetail> get(@PathVariable Long id) {
        return ResponseEntity.ok(planner.get(id));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<PlanDetail> execute(@PathVariable Long id,
                                              @RequestBody(required = false) ExecutePlanRequest request,
                                              Authentication auth) {
        return ResponseEntity.ok(planner.execute(id, request, auth != null ? auth.getName() : null));
    }
}
