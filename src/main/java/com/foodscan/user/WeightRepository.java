package com.foodscan.user;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightRepository extends JpaRepository<Weight, Weight.Key> {

    Optional<Weight> findFirstByUserIdOrderByDateDesc(Long userId);

    List<Weight> findByUserIdAndDateBetweenOrderByDate(Long userId, LocalDate from, LocalDate to);
}
