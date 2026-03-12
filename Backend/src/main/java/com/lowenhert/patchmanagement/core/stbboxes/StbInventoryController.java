package com.lowenhert.patchmanagement.core.stbboxes;

import com.lowenhert.patchmanagement.core.stbboxes.dto.CreateStbRequest;
import com.lowenhert.patchmanagement.core.stbboxes.dto.StbResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stbs")
@RequiredArgsConstructor
public class StbInventoryController {

    private final StbInventoryService service;

    @PostMapping
    public StbResponse create(@RequestBody CreateStbRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<StbResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{uuid}")
    public StbResponse getByUuid(@PathVariable UUID uuid) {
        return service.getByUuid(uuid);
    }

    @GetMapping("/vendor/{vendor}")
    public List<StbResponse> getByVendor(@PathVariable String vendor) {
        return service.getByVendor(vendor);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable UUID uuid) {
        service.delete(uuid);
    }
}
