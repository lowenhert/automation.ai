package com.lowenhert.patchmanagement.core.patch.repository;

import com.lowenhert.patchmanagement.core.patch.model.PatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PatchLogRepository extends JpaRepository<PatchLog, Long> {
    
    // Existing method - find logs by server ID
    List<PatchLog> findByServerId(Long serverId);
    
    // New method - find all logs with server details
    @Query("SELECT pl, s.hostname, s.ip FROM PatchLog pl JOIN Server s ON pl.serverId = s.id")
    List<Object[]> findAllLogsWithServerDetails();
    
    // New method - find log by ID with server details
    @Query("SELECT pl, s.hostname, s.ip FROM PatchLog pl JOIN Server s ON pl.serverId = s.id WHERE pl.id = :logId")
    List<Object[]> findLogByIdWithServerDetails(@Param("logId") Long logId);
}