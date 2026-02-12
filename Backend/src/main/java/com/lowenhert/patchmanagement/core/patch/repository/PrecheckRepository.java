package com.lowenhert.patchmanagement.core.patch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lowenhert.patchmanagement.core.patch.model.PrecheckResult;

public interface PrecheckRepository extends JpaRepository<PrecheckResult, Long> {}
