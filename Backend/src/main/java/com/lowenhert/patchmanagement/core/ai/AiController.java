package com.lowenhert.patchmanagement.core.ai;

import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanDraft;
import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanningService;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiHealthService healthService;
    private final TestPlanningService testPlanningService;

    /** 200 when Ollama is reachable and the model is available; 503 with diagnostics otherwise. */
    @GetMapping("/health")
    public ResponseEntity<AiHealthService.AiHealth> health(@RequestParam(defaultValue = "false") boolean probe) {
        AiHealthService.AiHealth health = healthService.check(probe);
        return ResponseEntity.status(health.isUp() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(health);
    }

    @PostMapping("/test-plans/draft")
    public ResponseEntity<TestPlanDraft> draftTestPlan(@RequestBody DraftRequest request) {
        if (request == null) throw new BadRequestException("Request body is required");
        return ResponseEntity.ok(testPlanningService.draft(request.goal(), request.context()));
    }

    public record DraftRequest(String goal, String context) {
    }
}
