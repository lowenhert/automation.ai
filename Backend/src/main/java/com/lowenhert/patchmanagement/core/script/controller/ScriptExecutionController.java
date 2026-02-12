package com.lowenhert.patchmanagement.core.script.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.lowenhert.patchmanagement.core.script.dto.ScriptDTO;
import com.lowenhert.patchmanagement.core.script.dto.ScriptExecutionResponseDTO;
import com.lowenhert.patchmanagement.core.script.service.ScriptExecutorService;
import com.lowenhert.patchmanagement.core.script.service.ScriptManagementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/scripts")
@RequiredArgsConstructor
public class ScriptExecutionController {
    private final ScriptExecutorService executorService;
    private final ScriptManagementService scriptManagementService;
    
    @PostMapping("/{id}/execute")
    public ScriptExecutionResponseDTO executeScript(@PathVariable Long id) {
        return executorService.executeScript(id);
    }
    
    @GetMapping("/executions/{id}")
    public ScriptExecutionResponseDTO getExecutionStatus(@PathVariable Long id) {
        return executorService.getExecutionStatus(id);
    }
   
    @PostMapping("/{id}/execute-stream")
    public ResponseEntity<StreamingResponseBody> executeAndStream(@PathVariable Long id) {
        StreamingResponseBody body = outputStream -> {
            try {
                executorService.executeScriptStream(id, outputStream);
            } catch (Exception e) {
                outputStream.write(("Error: " + e.getMessage() + "\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                outputStream.flush();
                outputStream.close();
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .body(body);
    }


    @PostMapping
    public ScriptDTO createScript(@RequestBody ScriptDTO scriptDTO) {
        return scriptManagementService.createScript(scriptDTO);
    }

    @PutMapping("/{id}")
    public ScriptDTO updateScript(@PathVariable Long id, @RequestBody ScriptDTO scriptDTO) {
        return scriptManagementService.updateScript(id, scriptDTO);
    }

    @GetMapping("/{id}")
    public ScriptDTO getScript(@PathVariable Long id) {
        return scriptManagementService.getScriptById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteScript(@PathVariable Long id) {
        scriptManagementService.deleteScript(id);
    }
    @GetMapping
    public List<ScriptDTO> getAllScripts() {
        return scriptManagementService.getScripts();
    }
}