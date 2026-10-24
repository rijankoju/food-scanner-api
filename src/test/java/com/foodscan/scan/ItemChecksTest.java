package com.foodscan.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.foodscan.food.Food;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ItemChecksTest {

    private static MealAnalysis.Item item(double grams, double low, double high, double confidence,
                                          double kcal, double protein, double carbs, double fat, boolean hidden) {
        return new MealAnalysis.Item("rice", "Rice, white, cooked", "boiled", hidden, grams, low, high, confidence,
                kcal, protein, carbs, fat);
    }

    @Test
    void normalizeClampsAndOrdersRanges() {
        MealAnalysis.Item n = ItemChecks.normalize(new MealAnalysis.Item(" ", "", null, false,
                5000, 6000, 10, 1.7, -3, 300, Double.NaN, 20));

        assertThat(n.name()).isEqualTo("Unknown food");
        assertThat(n.databaseQuery()).isEqualTo("Unknown food");
        assertThat(n.cookingMethod()).isEqualTo("unknown");
        assertThat(n.estimatedGrams()).isEqualTo(3000);
        assertThat(n.gramsLow()).isEqualTo(3000);
        assertThat(n.gramsHigh()).isEqualTo(3000);
        assertThat(n.confidence()).isEqualTo(1);
        assertThat(n.kcalPer100g()).isZero();
        assertThat(n.proteinPer100g()).isEqualTo(100);
        assertThat(n.carbsPer100g()).isZero();
    }

    @Test
    void flagsDatabaseItemWhoseCaloriesDisagreeWithModel() {
        Food food = new Food("usda", "1");
        food.setKcal100g(new BigDecimal("130"));

        assertThat(ItemChecks.flags(item(200, 150, 250, 0.9, 135, 2.7, 28, 0.3, false), food, true)).isEmpty();
        assertThat(ItemChecks.flags(item(200, 150, 250, 0.9, 180, 2.7, 28, 0.3, false), food, true))
                .containsExactly(ItemChecks.KCAL_MISMATCH);
    }

    @Test
    void flagsUnmatchedItemWithInconsistentMacros() {
        // 4*10 + 4*10 + 9*10 = 170, far from the claimed 400
        assertThat(ItemChecks.flags(item(100, 80, 120, 0.3, 400, 10, 10, 10, false), null, false))
                .containsExactly(ItemChecks.LOW_CONFIDENCE, ItemChecks.NO_DB_MATCH, ItemChecks.MACRO_INCONSISTENT);
        assertThat(ItemChecks.flags(item(1200, 900, 1500, 0.8, 170, 10, 10, 10, true), null, false))
                .containsExactly(ItemChecks.HIDDEN_ADDITION, ItemChecks.LARGE_PORTION, ItemChecks.NO_DB_MATCH);
    }
}
