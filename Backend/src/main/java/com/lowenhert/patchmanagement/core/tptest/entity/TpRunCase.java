package com.lowenhert.patchmanagement.core.tptest.entity;

import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tp_run_case")
public class TpRunCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "latest_status")
    private String latestStatus; // PASSED, FAILED, BLOCKED, RETEST (null = untested)
}
