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
public class PatchCriticality {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long serverId;
    private String packageName;
    private String severity; // e.g. "CRITICAL", "IMPORTANT", "LOW"
    private String description;
    private LocalDateTime detectedAt;
}
