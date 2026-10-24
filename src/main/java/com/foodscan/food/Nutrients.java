package com.foodscan.food;

import java.math.BigDecimal;

/** Core macros for some amount of food; null means the source has no value. */
public record Nutrients(Double kcal, Double proteinG, Double carbsG, Double fatG) {

    public static Nutrients of(BigDecimal kcal, BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        return new Nutrients(toDouble(kcal), toDouble(protein), toDouble(carbs), toDouble(fat));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
