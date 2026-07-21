package com.lowenhert.patchmanagement.core.stbreports;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stb_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StbReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stb_no", nullable = false)
    private Integer stbNo;

    @Column(name = "box_name", nullable = false)
    private String boxName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "report_date")
    private String reportDate;

    @Column(name = "total_tests")
    private Integer totalTests;

    @Column(name = "passed")
    private Integer passed;

    @Column(name = "failed")
    private Integer failed;

    @Lob
    @Column(name = "file_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] fileData;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    public void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (reportDate == null) {
            reportDate = java.time.LocalDate.now().toString();
        }
    }
}
