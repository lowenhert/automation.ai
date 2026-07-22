package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpProjectRepository extends JpaRepository<TpProject, Long> {
    List<TpProject> findAllByOrderByCreatedAtDesc();
}
