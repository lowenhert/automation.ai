package com.lowenhert.patchmanagement.core.patch.controller;

import com.lowenhert.patchmanagement.core.patch.model.PatchLog;
import com.lowenhert.patchmanagement.core.patch.model.PatchSchedule;
import com.lowenhert.patchmanagement.core.patch.service.PatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patch")
public class PatchController {

    private final PatchService patchService;

    public PatchController(PatchService patchService) {
        this.patchService = patchService;
    }

    @PostMapping("/run/{serverId}")
    public ResponseEntity<String> runPatch(@PathVariable Long serverId) {
        return ResponseEntity.ok(patchService.runPatch(serverId));
    }

    @GetMapping("/logs/{serverId}")
    public ResponseEntity<List<PatchLog>> getLogs(@PathVariable Long serverId) {
        return ResponseEntity.ok(patchService.getPatchLogs(serverId));
    }

    @PostMapping("/schedule/{serverId}")
    public ResponseEntity<String> schedulePatch(@PathVariable Long serverId,
                                                @RequestParam String when) {
        LocalDateTime time = LocalDateTime.parse(when);
        return ResponseEntity.ok(patchService.schedulePatch(serverId, time));
    }
    
 // Cancel a running job
    @PostMapping("/cancel/job/{jobId}")
    public ResponseEntity<String> cancelJob(@PathVariable String jobId) {
        return ResponseEntity.ok(patchService.cancelJob(jobId));
    }

    // Cancel a scheduled patch
    @PostMapping("/cancel/schedule/{scheduleId}")
    public ResponseEntity<String> cancelSchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(patchService.cancelScheduledPatch(scheduleId));
    }
    
    @GetMapping("/analyze/{serverId}")
    public ResponseEntity<Map<String, Object>> analyzeLogs(@PathVariable Long serverId) {
        Map<String, Object> analysis = patchService.analyzePatchLogs(serverId);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/schedule/{scheduleId}")
    public ResponseEntity<PatchSchedule> getSchedule(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(patchService.getSchedule(scheduleId));
    }

    @GetMapping("/schedules")
    public ResponseEntity<List<PatchSchedule>> getAllSchedules() {
        return ResponseEntity.ok(patchService.getAllSchedules());
    }
    
 // Add these methods to PatchController class
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getAllLogs(
            @RequestParam(required = false) String hostname,
            @RequestParam(required = false) String patchType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        List<Map<String, Object>> filteredLogs = patchService.getAllLogsWithServerDetails();
        
        // Apply filters
        if (hostname != null && !hostname.isEmpty()) {
            filteredLogs = filteredLogs.stream()
                    .filter(log -> hostname.equals(log.get("hostname")))
                    .collect(Collectors.toList());
        }
        
        if (patchType != null && !patchType.isEmpty()) {
            filteredLogs = filteredLogs.stream()
                    .filter(log -> patchType.equals(log.get("patchType")))
                    .collect(Collectors.toList());
        }
        
        if (status != null && !status.isEmpty()) {
            filteredLogs = filteredLogs.stream()
                    .filter(log -> status.equals(log.get("status")))
                    .collect(Collectors.toList());
        }
        
        // Apply pagination
        int totalCount = filteredLogs.size();
        int start = Math.min(page * size, totalCount);
        int end = Math.min((page + 1) * size, totalCount);
        
        List<Map<String, Object>> paginatedLogs = filteredLogs.subList(start, end);
        
        Map<String, Object> response = new HashMap<>();
        response.put("rows", paginatedLogs);
        response.put("count", totalCount);
        
        return ResponseEntity.ok(response);
    }
    

    @GetMapping("/logs/id/{logId}")
    public ResponseEntity<Map<String, Object>> getLogById(@PathVariable Long logId) {
        return ResponseEntity.ok(patchService.getLogByIdWithServerDetails(logId));
    }

    @GetMapping("/logs/details/{serverId}")
    public ResponseEntity<List<Map<String, Object>>> getLogsWithServerDetails(@PathVariable Long serverId) {
        return ResponseEntity.ok(patchService.getPatchLogsWithServerDetails(serverId));
    }

}
