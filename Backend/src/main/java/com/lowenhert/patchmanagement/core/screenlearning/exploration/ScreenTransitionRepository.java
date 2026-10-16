package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreenTransitionRepository extends JpaRepository<ScreenTransition, Long> {

    Optional<ScreenTransition> findByFromNodeIdAndActionAndToNodeId(Long fromNodeId, RemoteKey action, Long toNodeId);

    @Query("SELECT t FROM ScreenTransition t JOIN FETCH t.fromNode JOIN FETCH t.toNode WHERE t.deviceSerial = :serial")
    List<ScreenTransition> findGraphEdges(@Param("serial") String deviceSerial);
}
