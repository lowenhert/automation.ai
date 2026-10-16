package com.lowenhert.patchmanagement.core.screenlearning.history;

import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenDetailDto;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/screens")
@RequiredArgsConstructor
public class ScreenHistoryController {

    private final ScreenHistoryService historyService;

    @GetMapping
    public ResponseEntity<PageResponse<ScreenSummaryDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String deviceId) {
        return ResponseEntity.ok(historyService.list(page, size, deviceId));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<ScreenSummaryDto>> search(
            @RequestParam String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(historyService.search(text, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScreenDetailDto> get(@PathVariable String id) {
        return ResponseEntity.ok(historyService.get(id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable String id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(new FileSystemResource(historyService.imageFile(id)));
    }
}
