package com.foodscan.user;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** The goal in force on {@code date}. */
    Optional<Goal> findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(Long userId, LocalDate date);

    Optional<Goal> findFirstByUserIdOrderByEffectiveFromAsc(Long userId);

    Optional<Goal> findByUserIdAndEffectiveFrom(Long userId, LocalDate effectiveFrom);
}
