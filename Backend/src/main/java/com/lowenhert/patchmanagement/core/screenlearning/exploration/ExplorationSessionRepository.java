package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExplorationSessionRepository extends JpaRepository<ExplorationSession, Long> {

    Page<ExplorationSession> findAllBy(Pageable pageable);

    List<ExplorationSession> findByStatusIn(Collection<ExplorationSession.Status> statuses);
}
