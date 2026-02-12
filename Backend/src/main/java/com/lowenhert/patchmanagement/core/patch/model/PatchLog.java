package com.lowenhert.patchmanagement.core.patch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patch_logs")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_id", nullable = false)
    private Long serverId;

    @Column(name = "patch_type")
    private String patchType; // PRECHECK / PATCH / POSTCHECK

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
