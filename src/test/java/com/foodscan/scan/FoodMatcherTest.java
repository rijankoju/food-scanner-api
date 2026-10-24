package com.foodscan.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodscan.food.FoodRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class FoodMatcherTest {

    record Row(Long getId, String getName, String getDataType, String getCategory, BigDecimal getKcal100g,
               BigDecimal getProtein100g, BigDecimal getCarbs100g, BigDecimal getFat100g, Double getScore)
            implements FoodRepository.SearchRow {
    }

    private static Row row(long id, String name, int kcal, double score) {
        return new Row(id, name, "Survey (FNDDS)", null, BigDecimal.valueOf(kcal), null, null, null, score);
    }

    @Test
    void breaksNearTiesWithTheModelsCalorieEstimate() {
        // Real search output for "banana": all four tie on text score.
        List<FoodRepository.SearchRow> rows = List.of(
                row(1, "Banana chips", 519, 0.588),
                row(2, "Banana split", 199, 0.588),
                row(3, "Banana, raw", 97, 0.588),
                row(4, "Banana, baked", 161, 0.588));

        assertThat(FoodMatcher.bestOf(rows, 90)).get().extracting(FoodMatcher.Match::foodId).isEqualTo(3L);
        assertThat(FoodMatcher.bestOf(rows, 500)).get().extracting(FoodMatcher.Match::foodId).isEqualTo(1L);
    }

    @Test
    void clearlyBetterTextMatchWinsOverCalories() {
        List<FoodRepository.SearchRow> rows = List.of(
                row(1, "Rice, fried, with pork", 179, 0.60),
                row(2, "Rice, white, cooked", 130, 0.50));

        assertThat(FoodMatcher.bestOf(rows, 130)).get().extracting(FoodMatcher.Match::foodId).isEqualTo(1L);
    }

    @Test
    void weakHitsAreNotMatches() {
        // "dal" matched a chocolate bar at 0.375 in real data
        List<FoodRepository.SearchRow> rows = List.of(row(1, "Candies, SPECIAL DARK Chocolate Bar", 556, 0.375));

        assertThat(FoodMatcher.bestOf(rows, 120)).isEmpty();
        assertThat(FoodMatcher.bestOf(List.of(), 120)).isEmpty();
    }
}
