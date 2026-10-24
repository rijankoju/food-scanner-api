package com.foodscan.usda;

import java.util.List;

/** One FoodData Central food, normalized to per-100 g values. */
record UsdaFood(
        String fdcId,
        String dataType,
        String name,
        String category,
        Double kcal,
        Double proteinG,
        Double carbsG,
        Double fatG,
        Double fiberG,
        Double sugarG,
        Double sodiumMg,
        String energyBasis,
        List<Portion> portions) {

    record Portion(String label, double grams) {
    }

    boolean hasMacros() {
        return kcal != null || proteinG != null || carbsG != null || fatG != null;
    }
}
