package com.foodscan.scan;

import com.foodscan.food.Food;
import java.util.ArrayList;
import java.util.List;

/**
 * Sanity checks that decide which items the app highlights for review, and
 * normalization of raw model output into safe ranges.
 */
final class ItemChecks {

    static final String LOW_CONFIDENCE = "low_confidence";
    static final String NO_DB_MATCH = "no_db_match";
    static final String KCAL_MISMATCH = "kcal_mismatch";
    static final String LARGE_PORTION = "large_portion";
    static final String MACRO_INCONSISTENT = "macro_inconsistent";
    static final String HIDDEN_ADDITION = "hidden_addition";

    private ItemChecks() {
    }

    /** Clamps model output to plausible ranges and orders the gram range. */
    static MealAnalysis.Item normalize(MealAnalysis.Item item) {
        double grams = clamp(item.estimatedGrams(), 1, 3000);
        double low = clamp(Math.min(item.gramsLow(), grams), 1, grams);
        double high = clamp(Math.max(item.gramsHigh(), grams), grams, 3000);
        String name = truncate(blankTo(item.name(), "Unknown food"), 200);
        return new MealAnalysis.Item(
                name,
                truncate(blankTo(item.databaseQuery(), name), 200),
                truncate(blankTo(item.cookingMethod(), "unknown"), 50),
                item.hiddenAddition(),
                grams, low, high,
                clamp(item.confidence(), 0, 1),
                clamp(item.kcalPer100g(), 0, 900),
                clamp(item.proteinPer100g(), 0, 100),
                clamp(item.carbsPer100g(), 0, 100),
                clamp(item.fatPer100g(), 0, 100));
    }

    static List<String> flags(MealAnalysis.Item ai, Food matched, boolean fromDatabase) {
        List<String> flags = new ArrayList<>();
        if (ai.hiddenAddition()) {
            flags.add(HIDDEN_ADDITION);
        }
        if (ai.confidence() < 0.5) {
            flags.add(LOW_CONFIDENCE);
        }
        if (ai.estimatedGrams() > 1000) {
            flags.add(LARGE_PORTION);
        }
        if (!fromDatabase) {
            flags.add(NO_DB_MATCH);
            // Atwater check on the model's own numbers: 4 kcal/g protein and carbs, 9 kcal/g fat.
            double atwater = 4 * ai.proteinPer100g() + 4 * ai.carbsPer100g() + 9 * ai.fatPer100g();
            if (ai.kcalPer100g() >= 20 && Math.abs(atwater - ai.kcalPer100g()) / ai.kcalPer100g() > 0.15) {
                flags.add(MACRO_INCONSISTENT);
            }
        } else if (matched.getKcal100g() != null && matched.getKcal100g().doubleValue() > 0 && ai.kcalPer100g() > 0) {
            double db = matched.getKcal100g().doubleValue();
            if (Math.abs(ai.kcalPer100g() - db) / db > 0.25) {
                flags.add(KCAL_MISMATCH);
            }
        }
        return flags;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
