package com.lowenhert.patchmanagement.core.ott;

import com.lowenhert.patchmanagement.core.ott.dto.OttAutomationTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OttAutomationTestResultRepository
        extends JpaRepository<OttAutomationTestResult, Long> {

    List<OttAutomationTestResult> findByOttAppName(String ottAppName);

    List<OttAutomationTestResult> findByStatus(String status);

    List<OttAutomationTestResult> findByDeviceId(String deviceId);

    List<OttAutomationTestResult> findByOttAppNameAndStatus(String ottAppName, String status);

}