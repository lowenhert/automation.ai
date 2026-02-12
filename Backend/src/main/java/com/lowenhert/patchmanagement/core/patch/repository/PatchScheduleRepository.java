package com.lowenhert.patchmanagement.core.patch.repository;

import com.lowenhert.patchmanagement.core.patch.model.PatchSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PatchScheduleRepository extends JpaRepository<PatchSchedule, Long> {
    
    // Find schedules by server ID
    List<PatchSchedule> findByServerId(Long serverId);
    
    // Find schedules by status
    List<PatchSchedule> findByStatus(String status);
    
    // Find upcoming schedules (scheduled after a specific time and with specific status)
    List<PatchSchedule> findByScheduledAtAfterAndStatus(LocalDateTime scheduledAt, String status);
    
    // Find schedules by AWX schedule ID
    PatchSchedule findByAwxScheduleId(String awxScheduleId);
    
    // Find expired schedules that are still in SCHEDULED status
    @Query("SELECT ps FROM PatchSchedule ps WHERE ps.scheduledAt < :currentTime AND ps.status = 'SCHEDULED'")
    List<PatchSchedule> findExpiredSchedules(@Param("currentTime") LocalDateTime currentTime);
    
    // Find schedules by server ID and status
    List<PatchSchedule> findByServerIdAndStatus(Long serverId, String status);
    
    // Find schedules within a date range
    List<PatchSchedule> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Count schedules by status
    long countByStatus(String status);
}