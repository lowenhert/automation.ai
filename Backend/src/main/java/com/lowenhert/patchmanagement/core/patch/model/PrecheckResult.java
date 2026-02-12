package com.lowenhert.patchmanagement.core.patch.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrecheckResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long serverId;
    private String diskUsage;
    private String memoryStatus;
    private String uptime;
    private String packageUpdates;
    private LocalDateTime timestamp;
}
