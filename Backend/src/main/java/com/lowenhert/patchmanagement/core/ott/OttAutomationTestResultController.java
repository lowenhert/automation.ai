package com.lowenhert.patchmanagement.core.ott;

import com.lowenhert.patchmanagement.core.ott.dto.OttAutomationTestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ott-results")
public class OttAutomationTestResultController {

    private final OttAutomationTestResultService service;

    public OttAutomationTestResultController(OttAutomationTestResultService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OttAutomationTestResult> save(@RequestBody OttAutomationTestResult result) {
        log.info("Saving OTT test result: app={}, device={}", result.getOttAppName(), result.getDeviceId());
        OttAutomationTestResult saved = service.save(result);
        log.info("OTT test result saved: id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<OttAutomationTestResult>> getAll() {
        log.debug("Fetching all OTT test results");
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OttAutomationTestResult> getById(@PathVariable Long id) {
        log.debug("Fetching OTT test result by id: {}", id);
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/app/{app}")
    public ResponseEntity<List<OttAutomationTestResult>> getByApp(@PathVariable String app) {
        log.debug("Fetching OTT test results by app: {}", app);
        return ResponseEntity.ok(service.getByApp(app));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OttAutomationTestResult>> getByStatus(@PathVariable String status) {
        log.debug("Fetching OTT test results by status: {}", status);
        return ResponseEntity.ok(service.getByStatus(status));
    }

    @GetMapping("/device/{device}")
    public ResponseEntity<List<OttAutomationTestResult>> getByDevice(@PathVariable String device) {
        log.debug("Fetching OTT test results by device: {}", device);
        return ResponseEntity.ok(service.getByDevice(device));
    }

    @GetMapping("/filter")
    public ResponseEntity<List<OttAutomationTestResult>> filter(
            @RequestParam String app,
            @RequestParam String status) {
        log.debug("Filtering OTT test results: app={}, status={}", app, status);
        return ResponseEntity.ok(service.getByAppAndStatus(app, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Delete OTT test result: id={}", id);
        service.delete(id);
        log.info("OTT test result deleted: id={}", id);
        return ResponseEntity.noContent().build();
    }
}