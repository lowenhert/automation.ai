package com.lowenhert.patchmanagement.core.stbreports;

import com.lowenhert.patchmanagement.core.stbreports.dto.StbReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/stb-reports")
@RequiredArgsConstructor
public class StbReportController {

    private final StbReportService service;

    @PostMapping("/upload")
    public ResponseEntity<StbReportResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("stbNo") Integer stbNo,
            @RequestParam("boxName") String boxName) throws IOException {
        log.info("Upload report request: stbNo={}, boxName={}, fileName={}",
                stbNo, boxName, file.getOriginalFilename());
        StbReportResponse response = service.upload(file, stbNo, boxName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<StbReportResponse>> getAll() {
        log.debug("Fetching all reports");
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/box/{stbNo}")
    public ResponseEntity<List<StbReportResponse>> getByBox(@PathVariable Integer stbNo) {
        log.debug("Fetching reports for stbNo={}", stbNo);
        return ResponseEntity.ok(service.getByStbNo(stbNo));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        log.debug("Download report request: id={}", id);
        StbReport report = service.getReportEntity(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        report.getContentType() != null ? report.getContentType() : "application/octet-stream"))
                .body(report.getFileData());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Delete report request: id={}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
