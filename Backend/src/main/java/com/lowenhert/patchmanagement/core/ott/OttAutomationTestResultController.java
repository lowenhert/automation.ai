package com.lowenhert.patchmanagement.core.ott;

import com.lowenhert.patchmanagement.core.ott.dto.OttAutomationTestResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ott-results")
public class OttAutomationTestResultController {

    private final OttAutomationTestResultService service;

    public OttAutomationTestResultController(OttAutomationTestResultService service) {
        this.service = service;
    }

    @PostMapping
    public OttAutomationTestResult save(@RequestBody OttAutomationTestResult result){
        return service.save(result);
    }

    @GetMapping
    public List<OttAutomationTestResult> getAll(){
        return service.getAll();
    }

    @GetMapping("/{id}")
    public OttAutomationTestResult getById(@PathVariable Long id){
        return service.getById(id);
    }

    @GetMapping("/app/{app}")
    public List<OttAutomationTestResult> getByApp(@PathVariable String app){
        return service.getByApp(app);
    }

    @GetMapping("/status/{status}")
    public List<OttAutomationTestResult> getByStatus(@PathVariable String status){
        return service.getByStatus(status);
    }

    @GetMapping("/device/{device}")
    public List<OttAutomationTestResult> getByDevice(@PathVariable String device){
        return service.getByDevice(device);
    }

    @GetMapping("/filter")
    public List<OttAutomationTestResult> filter(
            @RequestParam String app,
            @RequestParam String status){
        return service.getByAppAndStatus(app,status);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){
        service.delete(id);
    }
}