package com.lowenhert.patchmanagement.core.patch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lowenhert.patchmanagement.core.patch.model.PatchCriticality;

public interface PatchCriticalityRepository extends JpaRepository<PatchCriticality, Long> {
	   // ✅ Fetch all patch criticalities for a specific server
    List<PatchCriticality> findByServerId(Long serverId);
}
