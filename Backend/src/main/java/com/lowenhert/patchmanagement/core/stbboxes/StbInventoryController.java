package com.lowenhert.patchmanagement.core.stbboxes;

import com.lowenhert.patchmanagement.core.stbboxes.dto.CreateStbRequest;
import com.lowenhert.patchmanagement.core.stbboxes.dto.StbResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/stbs")
@RequiredArgsConstructor
public class StbInventoryController {

    private final StbInventoryService service;

    @PostMapping
    public ResponseEntity<StbResponse> create(@RequestBody CreateStbRequest request) {
        log.info("Create STB request: vendor={}, model={}", request.getVendor(), request.getModel());
        StbResponse created = service.create(request);
        log.info("STB created: uuid={}", created.getUuid());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<StbResponse>> getAll() {
        log.debug("Fetching all STBs");
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<StbResponse> getByUuid(@PathVariable UUID uuid) {
        log.debug("Fetching STB by uuid: {}", uuid);
        return ResponseEntity.ok(service.getByUuid(uuid));
    }

    @GetMapping("/vendor/{vendor}")
    public ResponseEntity<List<StbResponse>> getByVendor(@PathVariable String vendor) {
        log.debug("Fetching STBs by vendor: {}", vendor);
        return ResponseEntity.ok(service.getByVendor(vendor));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        log.info("Delete STB request: uuid={}", uuid);
        service.delete(uuid);
        log.info("STB deleted: uuid={}", uuid);
        return ResponseEntity.noContent().build();
    }
}
