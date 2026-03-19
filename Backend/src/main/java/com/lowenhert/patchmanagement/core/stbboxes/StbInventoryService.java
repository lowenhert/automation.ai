package com.lowenhert.patchmanagement.core.stbboxes;

import com.lowenhert.patchmanagement.core.config.exception.ConflictException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.stbboxes.dto.CreateStbRequest;
import com.lowenhert.patchmanagement.core.stbboxes.dto.StbResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StbInventoryService {

    private final StbInventoryRepository repository;

    public StbResponse create(CreateStbRequest request) {
        if (repository.existsByVendorAndStbNo(
                request.getVendor(), request.getStbNo())) {
            log.warn("Duplicate STB: vendor={}, stbNo={}", request.getVendor(), request.getStbNo());
            throw new ConflictException("STB already exists for vendor '" + request.getVendor() + "' with stbNo " + request.getStbNo());
        }

        StbInventory stb = StbInventory.builder()
                .vendor(request.getVendor())
                .model(request.getModel())
                .resolution(request.getResolution())
                .stbNo(request.getStbNo())
                .build();

        repository.save(stb);
        log.info("STB created: uuid={}, vendor={}", stb.getUuid(), stb.getVendor());
        return mapToResponse(stb);
    }

    public List<StbResponse> getAll() {
        List<StbResponse> result = repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        log.debug("Fetched {} STBs", result.size());
        return result;
    }

    public StbResponse getByUuid(UUID uuid) {
        StbInventory stb = repository.findByUuid(uuid)
                .orElseThrow(() -> {
                    log.warn("STB not found: uuid={}", uuid);
                    return new ResourceNotFoundException("STB not found with uuid: " + uuid);
                });
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
                .orElseThrow(() -> {
                    log.warn("Delete failed — STB not found: uuid={}", uuid);
                    return new ResourceNotFoundException("STB not found with uuid: " + uuid);
                });
        repository.delete(stb);
        log.info("STB deleted: uuid={}", uuid);
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
