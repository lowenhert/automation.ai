package com.lowenhert.patchmanagement.core.ott;

import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.ott.dto.OttAutomationTestResult;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OttAutomationTestResultService {

    private final OttAutomationTestResultRepository repository;

    public OttAutomationTestResultService(OttAutomationTestResultRepository repository) {
        this.repository = repository;
    }

    public OttAutomationTestResult save(OttAutomationTestResult result) {
        OttAutomationTestResult saved = repository.save(result);
        log.info("OTT test result saved: id={}, app={}", saved.getId(), saved.getOttAppName());
        return saved;
    }

    public List<OttAutomationTestResult> getAll() {
        List<OttAutomationTestResult> results = repository.findAll();
        log.debug("Fetched {} OTT test results", results.size());
        return results;
    }

    public OttAutomationTestResult getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("OTT test result not found: id={}", id);
                    return new ResourceNotFoundException("OTT test result not found with id: " + id);
                });
    }

    public List<OttAutomationTestResult> getByApp(String app) {
        return repository.findByOttAppName(app);
    }

    public List<OttAutomationTestResult> getByStatus(String status) {
        return repository.findByStatus(status);
    }

    public List<OttAutomationTestResult> getByDevice(String device) {
        return repository.findByDeviceId(device);
    }

    public List<OttAutomationTestResult> getByAppAndStatus(String app, String status) {
        return repository.findByOttAppNameAndStatus(app, status);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            log.warn("Delete failed — OTT test result not found: id={}", id);
            throw new ResourceNotFoundException("OTT test result not found with id: " + id);
        }
        repository.deleteById(id);
        log.info("OTT test result deleted: id={}", id);
    }
}