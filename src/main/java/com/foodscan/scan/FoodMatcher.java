package com.foodscan.scan;

import com.foodscan.food.FoodRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Finds the database food for a detected item. Text search ranks candidates;
 * among near-equal hits, the one whose calories are closest to the model's own
 * per-100 g estimate wins, which separates e.g. "banana, raw" from "banana
 * chips" when the query alone can't.
 */
@Component
class FoodMatcher {

    /** Below this search score a hit is not a real match (e.g. "dal" vs a chocolate bar scored 0.375). */
    static final double MIN_SCORE = 0.45;

    /** Hits within this distance of the best score count as ties. */
    static final double TIE_MARGIN = 0.03;

    record Match(long foodId, double score) {
    }

    private final FoodRepository foods;

    FoodMatcher(FoodRepository foods) {
        this.foods = foods;
    }

    Optional<Match> match(MealAnalysis.Item item) {
        Optional<Match> match = bestOf(foods.search(item.databaseQuery(), 8), item.kcalPer100g());
        if (match.isEmpty() && !item.name().equalsIgnoreCase(item.databaseQuery())) {
            match = bestOf(foods.search(item.name(), 8), item.kcalPer100g());
        }
        return match;
    }

    static Optional<Match> bestOf(List<FoodRepository.SearchRow> rows, double aiKcalPer100g) {
        if (rows.isEmpty() || rows.getFirst().getScore() < MIN_SCORE) {
            return Optional.empty();
        }
        double best = rows.getFirst().getScore();
        return rows.stream()
                .filter(r -> r.getScore() >= best - TIE_MARGIN)
                .min(Comparator.comparingDouble(r -> kcalDistance(r, aiKcalPer100g)))
                .map(r -> new Match(r.getId(), r.getScore()));
    }

    private static double kcalDistance(FoodRepository.SearchRow row, double aiKcal) {
        if (row.getKcal100g() == null || aiKcal <= 0) {
            return Double.MAX_VALUE;
        }
        return Math.abs(row.getKcal100g().doubleValue() - aiKcal);
    }
}
