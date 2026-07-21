package com.lowenhert.patchmanagement.core.stbreports.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StbReportResponse {

    private Long id;
    private Integer stbNo;
    private String boxName;
    private String fileName;
    private String contentType;
    private String reportDate;
    private Integer totalTests;
    private Integer passed;
    private Integer failed;
    private LocalDateTime uploadedAt;
}
