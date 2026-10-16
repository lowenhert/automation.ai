package com.lowenhert.patchmanagement.core.ai.planner;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiTestPlanRepository extends JpaRepository<AiTestPlan, Long> {

    Page<AiTestPlan> findAllBy(Pageable pageable);

    Page<AiTestPlan> findByDeviceSerial(String deviceSerial, Pageable pageable);

    Page<AiTestPlan> findByStatus(AiTestPlan.Status status, Pageable pageable);
}
