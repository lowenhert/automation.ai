package com.lowenhert.patchmanagement.core.stbboxes;
import com.lowenhert.patchmanagement.core.stbboxes.dto.Resolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StbInventoryRepository extends JpaRepository<StbInventory, Long> {

    Optional<StbInventory> findByUuid(UUID uuid);

    List<StbInventory> findByVendor(String vendor);

    List<StbInventory> findByModel(String model);

    List<StbInventory> findByResolution(Resolution resolution);

    boolean existsByVendorAndStbNo(String vendor, Integer stbNo);
}
