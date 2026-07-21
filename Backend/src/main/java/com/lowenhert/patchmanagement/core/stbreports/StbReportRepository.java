package com.lowenhert.patchmanagement.core.stbreports;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StbReportRepository extends JpaRepository<StbReport, Long> {

    List<StbReport> findByStbNo(Integer stbNo);
}
