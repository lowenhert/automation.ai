package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenNodeRepository extends JpaRepository<ScreenNode, Long> {

    List<ScreenNode> findByDeviceSerial(String deviceSerial);
}
