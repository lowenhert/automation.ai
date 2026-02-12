package com.lowenhert.patchmanagement.core.patch.controller;

import com.lowenhert.patchmanagement.core.patch.dto.AddServerRequest;
import com.lowenhert.patchmanagement.core.patch.model.Server;
import com.lowenhert.patchmanagement.core.patch.service.ServerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final ServerService serverService;

    public ServerController(ServerService serverService) {
        this.serverService = serverService;
    }

    // ✅ Add a new server
    @PostMapping
    public ResponseEntity<Server> addServer(@RequestBody AddServerRequest request) {
        Server created = serverService.addServer(request);
        return ResponseEntity.ok(created);
    }

    // ✅ List all servers with count
    @GetMapping
    public ResponseEntity<Map<String, Object>> listServers() {
        List<Server> servers = serverService.listServers();
        long count = serverService.getServerCount();

        Map<String, Object> response = new HashMap<>();
        response.put("count", count);
        response.put("servers", servers);

        return ResponseEntity.ok(response);
    }
    
    // ✅ Get full server details by ID (info + patch logs + patch criticality)
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getServerDetails(@PathVariable Long id) {
        Map<String, Object> details = serverService.getServerDetails(id);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    // ✅ NEW: Get comprehensive dashboard data for compliance and reporting
    @GetMapping("/dashboard/compliance")
    public ResponseEntity<Map<String, Object>> getComplianceDashboard() {
        Map<String, Object> dashboardData = serverService.getComplianceDashboardData();
        return ResponseEntity.ok(dashboardData);
    }
}