package com.lowenhert.patchmanagement.core.ott.dto;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ottautomationtestresult")
public class OttAutomationTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ott_app_name")
    private String ottAppName;

    @Column(name = "content_name")
    private String contentName;

    @Column(name = "playback_time_seconds")
    private Integer playbackTimeSeconds;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "status")
    private String status;

    @Column(name = "screenshots_folder")
    private String screenshotsFolder;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "failed_step")
    private String failedStep;
}