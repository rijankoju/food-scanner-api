package com.foodscan.scan;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScanRepository extends JpaRepository<Scan, Long> {

    @EntityGraph(attributePaths = {"items", "items.matchedFood"})
    Optional<Scan> findWithItemsByIdAndUserId(Long id, Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
}

interface ScanCorrectionRepository extends JpaRepository<ScanCorrection, Long> {
}
