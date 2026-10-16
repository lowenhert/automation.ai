package com.lowenhert.patchmanagement.core.testlibrary;

import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static com.lowenhert.patchmanagement.core.testlibrary.TestCaseController.user;

@RestController
@RequestMapping("/api/test-scripts")
@RequiredArgsConstructor
public class TestScriptController {

    private final TestScriptService service;

    @GetMapping
    public ResponseEntity<PageResponse<ScriptSummary>> list(@RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            @RequestParam(required = false) Long testCaseId) {
        return ResponseEntity.ok(service.list(page, size, testCaseId));
    }

    @PostMapping
    public ResponseEntity<ScriptDetail> create(@RequestBody ScriptRequest request, Authentication auth) {
        return ResponseEntity.ok(service.create(request, user(auth)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScriptDetail> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** Saving changed content returns the script to DRAFT. */
    @PutMapping("/{id}")
    public ResponseEntity<ScriptDetail> update(@PathVariable Long id, @RequestBody ScriptUpdateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PostMapping("/{id}/rename")
    public ResponseEntity<ScriptDetail> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        return ResponseEntity.ok(service.rename(id, request == null ? null : request.name()));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ScriptDetail> duplicate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.duplicate(id, user(auth)));
    }

    /** Static checks only; the script is not executed. */
    @PostMapping("/{id}/validate")
    public ResponseEntity<ScriptDetail> validate(@PathVariable Long id) {
        return ResponseEntity.ok(service.validate(id));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Preview> preview(@PathVariable Long id) {
        return ResponseEntity.ok(service.preview(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ScriptDetail> approve(@PathVariable Long id, @RequestBody ApproveRequest request, Authentication auth) {
        return ResponseEntity.ok(service.approve(id, request, user(auth)));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ScriptDetail> revoke(@PathVariable Long id) {
        return ResponseEntity.ok(service.revoke(id));
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
}
