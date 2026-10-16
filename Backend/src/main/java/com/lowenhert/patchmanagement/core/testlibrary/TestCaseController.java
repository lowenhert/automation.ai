package com.lowenhert.patchmanagement.core.testlibrary;

import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test-cases")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseService service;

    @GetMapping
    public ResponseEntity<PageResponse<TestCaseSummary>> list(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.list(page, size, q));
    }

    @PostMapping
    public ResponseEntity<TestCaseDetail> create(@RequestBody TestCaseRequest request, Authentication auth) {
        return ResponseEntity.ok(service.create(request, user(auth)));
    }

    @PostMapping("/from-plan")
    public ResponseEntity<TestCaseDetail> fromPlan(@RequestBody FromPlanRequest request, Authentication auth) {
        return ResponseEntity.ok(service.fromPlan(request, user(auth)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestCaseDetail> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TestCaseDetail> update(@PathVariable Long id, @RequestBody TestCaseRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/rename")
    public ResponseEntity<TestCaseDetail> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return ResponseEntity.ok(service.rename(id, request == null ? null : request.name()));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<TestCaseDetail> duplicate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.duplicate(id, user(auth)));
    }

    @GetMapping("/{id}/delete-impact")
    public ResponseEntity<DeleteImpact> deleteImpact(@PathVariable Long id) {
        return ResponseEntity.ok(service.deleteImpact(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirm) {
        service.delete(id, confirm);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/events")
    public ResponseEntity<TestCaseDetail> replaceEvents(@PathVariable Long id, @RequestBody List<EventRequest> events) {
        return ResponseEntity.ok(service.replaceEvents(id, events));
    }

    @DeleteMapping("/{id}/events/{eventId}")
    public ResponseEntity<TestCaseDetail> deleteEvent(@PathVariable Long id, @PathVariable Long eventId) {
        return ResponseEntity.ok(service.deleteEvent(id, eventId));
    }

    /** Generates a DRAFT script from the events. Nothing is executed. */
    @PostMapping("/{id}/scripts/generate")
    public ResponseEntity<ScriptDetail> generate(@PathVariable Long id, @RequestBody GenerateRequest request, Authentication auth) {
        return ResponseEntity.ok(service.generateScript(id, request, user(auth)));
    }

    static String user(Authentication auth) {
        return auth != null ? auth.getName() : null;
    }
}
