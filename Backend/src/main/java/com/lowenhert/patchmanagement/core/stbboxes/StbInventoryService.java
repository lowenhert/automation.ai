package com.lowenhert.patchmanagement.core.stbboxes;
import com.lowenhert.patchmanagement.core.stbboxes.dto.CreateStbRequest;
import com.lowenhert.patchmanagement.core.stbboxes.dto.StbResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StbInventoryService {

    private final StbInventoryRepository repository;

    public StbResponse create(CreateStbRequest request) {

        if (repository.existsByVendorAndStbNo(
                request.getVendor(), request.getStbNo())) {
            throw new RuntimeException("STB already exists for vendor");
        }

        StbInventory stb = StbInventory.builder()
                .vendor(request.getVendor())
                .model(request.getModel())
                .resolution(request.getResolution())
                .stbNo(request.getStbNo())
                .build();

        repository.save(stb);
        return mapToResponse(stb);
    }

    public List<StbResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public StbResponse getByUuid(UUID uuid) {
        StbInventory stb = repository.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("STB not found"));
        return mapToResponse(stb);
    }

    public List<StbResponse> getByVendor(String vendor) {
        return repository.findByVendor(vendor)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void delete(UUID uuid) {
        StbInventory stb = repository.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("STB not found"));
        repository.delete(stb);
    }

    private StbResponse mapToResponse(StbInventory stb) {
        return StbResponse.builder()
                .uuid(stb.getUuid())
                .vendor(stb.getVendor())
                .model(stb.getModel())
                .resolution(stb.getResolution())
                .stbNo(stb.getStbNo())
                .build();
    }
}
