package com.lowenhert.patchmanagement.core.patch.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "patch_schedule")
public class PatchSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long serverId;

    private String templateName;

    private LocalDateTime scheduledAt;

    // SCHEDULED / RUNNING / COMPLETED / FAILED / CANCELLED
    private String status;

    // ✅ AWX-specific ID (returned after schedule creation)
    private String awxScheduleId;

    // ✅ For tracking the user or system who scheduled it (optional)
    private String createdBy;

    // ✅ Timestamps for auditing
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
