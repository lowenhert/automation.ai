package com.lowenhert.patchmanagement.core.testlibrary;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A Python/pytest or Robot Framework script. It can be executed only while APPROVED and only if its content still
 * has the hash that was approved and validated, so any edit after approval requires a new review.
 */
@Entity
@Table(name = "test_scripts", indexes = @Index(name = "idx_test_scripts_case", columnList = "test_case_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestScript {

    public enum Language { PYTEST, ROBOT }

    public enum Origin { MANUAL, GENERATED }

    public enum Status { DRAFT, APPROVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "test_case_id")
    private Long testCaseId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 16)
    private Language language;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16)
    private Origin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    /** Hash of the content the last successful validation ran on. */
    @Column(name = "validated_hash", length = 64)
    private String validatedHash;

    @Column(name = "validation_json", columnDefinition = "TEXT")
    private String validationJson;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "approved_hash", length = 64)
    private String approvedHash;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isExecutable() {
        return status == Status.APPROVED && contentHash.equals(approvedHash) && contentHash.equals(validatedHash);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.DRAFT;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
