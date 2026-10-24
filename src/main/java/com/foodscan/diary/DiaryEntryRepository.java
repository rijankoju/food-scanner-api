package com.foodscan.diary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {

    record DailyTotal(LocalDate date, BigDecimal kcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG) {
    }

    @EntityGraph(attributePaths = "food")
    List<DiaryEntry> findByUserIdAndDateOrderByCreatedAt(Long userId, LocalDate date);

    @EntityGraph(attributePaths = "food")
    Optional<DiaryEntry> findByIdAndUserId(Long id, Long userId);

    /** Foods the user logged most recently, once each, with the amount used last time. */
    @Query(nativeQuery = true, value = """
            SELECT f.id           AS "id",
                   f.name         AS "name",
                   f.kcal_100g    AS "kcal100g",
                   f.protein_100g AS "protein100g",
                   f.carbs_100g   AS "carbs100g",
                   f.fat_100g     AS "fat100g",
                   r.grams        AS "lastGrams"
              FROM (SELECT DISTINCT ON (food_id) food_id, grams, created_at
                      FROM diary_entries
                     WHERE user_id = :userId
                     ORDER BY food_id, created_at DESC) r
              JOIN foods f ON f.id = r.food_id
             ORDER BY r.created_at DESC
             LIMIT :limit
            """)
    List<RecentFoodRow> recentFoods(@Param("userId") Long userId, @Param("limit") int limit);

    interface RecentFoodRow {
        Long getId();
        String getName();
        BigDecimal getKcal100g();
        BigDecimal getProtein100g();
        BigDecimal getCarbs100g();
        BigDecimal getFat100g();
        BigDecimal getLastGrams();
    }

    @Query("""
            select new com.foodscan.diary.DiaryEntryRepository$DailyTotal(
                       e.date, sum(e.kcal), sum(e.proteinG), sum(e.carbsG), sum(e.fatG))
              from DiaryEntry e
             where e.userId = :userId and e.date between :from and :to
             group by e.date
             order by e.date
            """)
    List<DailyTotal> dailyTotals(@Param("userId") Long userId, @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);
}
