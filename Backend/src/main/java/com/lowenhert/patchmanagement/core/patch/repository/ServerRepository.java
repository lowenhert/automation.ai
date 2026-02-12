package com.lowenhert.patchmanagement.core.patch.repository;

import com.lowenhert.patchmanagement.core.patch.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository extends JpaRepository<Server, Long> {}
