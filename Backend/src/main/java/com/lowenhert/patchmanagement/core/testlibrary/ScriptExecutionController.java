package com.lowenhert.patchmanagement.core.testlibrary;

import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.testlibrary.TestLibraryDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static com.lowenhert.patchmanagement.core.testlibrary.TestCaseController.user;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ScriptExecutionController {

    private final ScriptExecutionService service;

    /** Runs an approved script on the selected device. Requires confirm=true and the reviewed content hash. */
    @PostMapping
    public ResponseEntity<ExecutionDto> start(@RequestBody ExecuteRequest request, Authentication auth) {
        return ResponseEntity.ok(service.start(request, user(auth)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ExecutionDto>> list(@RequestParam(required = false) Long scriptId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.list(scriptId, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<ExecutionDto> stop(@PathVariable Long id) {
        return ResponseEntity.ok(service.stop(id));
    }

    @GetMapping("/{id}/log")
    public ResponseEntity<LogChunk> log(@PathVariable Long id, @RequestParam(defaultValue = "0") long offset) {
        return ResponseEntity.ok(service.log(id, offset));
    }

    @GetMapping("/{id}/artifacts/{name}")
    public ResponseEntity<byte[]> artifact(@PathVariable Long id, @PathVariable String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(service.artifact(id, name));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean confirm) {
        service.delete(id, confirm);
        return ResponseEntity.noContent().build();
    }
}
