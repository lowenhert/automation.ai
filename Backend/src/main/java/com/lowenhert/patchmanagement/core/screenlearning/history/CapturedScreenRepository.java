package com.lowenhert.patchmanagement.core.screenlearning.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CapturedScreenRepository extends JpaRepository<CapturedScreen, Long> {

    Page<CapturedScreen> findAllBy(Pageable pageable);

    Page<CapturedScreen> findByDeviceSerial(String deviceSerial, Pageable pageable);

    Optional<CapturedScreen> findByCaptureId(String captureId);

    @EntityGraph(attributePaths = "textRegions")
    Optional<CapturedScreen> findWithTextRegionsByCaptureId(String captureId);

    /** {@code pattern} must already be lower-cased, LIKE-escaped with '!' and wrapped in '%'. */
    // '!' rather than '\' because MySQL also treats backslash as a string-literal escape
    @Query("SELECT s FROM CapturedScreen s WHERE LOWER(s.extractedText) LIKE :pattern ESCAPE '!'")
    Page<CapturedScreen> searchByText(@Param("pattern") String pattern, Pageable pageable);

    @Query("SELECT r FROM ScreenTextRegion r WHERE r.screen.id IN :screenIds AND LOWER(r.text) LIKE :pattern ESCAPE '!' "
            + "ORDER BY r.screen.id, r.regionIndex")
    List<ScreenTextRegion> findMatchingRegions(@Param("screenIds") Collection<Long> screenIds, @Param("pattern") String pattern);
}
