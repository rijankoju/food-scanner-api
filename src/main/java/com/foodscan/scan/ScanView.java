package com.foodscan.scan;

import com.foodscan.food.Food;
import com.foodscan.food.Nutrients;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/** API shape of a scan. Items removed by the user are left out. */
public record ScanView(long id, Scan.Status status, String error, String mealName, String notes, Instant createdAt,
                       List<Item> items, Nutrients totals) {

    public record FoodOption(long id, String name, Nutrients per100g) {
    }

    /**
     * {@code per100g} comes from the matched food; {@code nutrients} is for
     * the current {@code grams}. The app can recompute nutrients locally while
     * the user drags a grams slider.
     */
    public record Item(long id, String name, String cookingMethod, boolean hiddenAddition, BigDecimal grams,
                       BigDecimal estimatedGrams, BigDecimal gramsLow, BigDecimal gramsHigh, BigDecimal confidence,
                       ScanItem.Source source, FoodOption food, Nutrients nutrients, List<String> flags,
                       List<FoodOption> alternatives) {
    }

    static ScanView from(Scan scan, Function<ScanItem, List<FoodOption>> alternatives) {
        List<Item> items = scan.getItems().stream()
                .filter(i -> !i.isRemoved())
                .map(i -> item(i, alternatives.apply(i)))
                .toList();
        double kcal = 0, protein = 0, carbs = 0, fat = 0;
        for (Item item : items) {
            kcal += value(item.nutrients().kcal());
            protein += value(item.nutrients().proteinG());
            carbs += value(item.nutrients().carbsG());
            fat += value(item.nutrients().fatG());
        }
        Nutrients totals = new Nutrients(round(kcal), round(protein), round(carbs), round(fat));
        return new ScanView(scan.getId(), scan.getStatus(), scan.getError(), scan.getMealName(), scan.getNotes(),
                scan.getCreatedAt(), items, totals);
    }

    static Item item(ScanItem i, List<FoodOption> alternatives) {
        FoodOption food = option(i.getMatchedFood());
        double grams = i.getGrams().doubleValue();
        Nutrients per100g = food.per100g();
        Nutrients nutrients = new Nutrients(
                forGrams(per100g.kcal(), grams), forGrams(per100g.proteinG(), grams),
                forGrams(per100g.carbsG(), grams), forGrams(per100g.fatG(), grams));
        return new Item(i.getId(), i.getAiName(), i.getCookingMethod(), i.isHiddenAddition(), i.getGrams(),
                i.getEstGrams(), i.getGramsLow(), i.getGramsHigh(), i.getConfidence(), i.getSource(), food,
                nutrients, i.getFlags(), alternatives);
    }

    static FoodOption option(Food food) {
        return new FoodOption(food.getId(), food.getName(), Nutrients.of(
                food.getKcal100g(), food.getProtein100g(), food.getCarbs100g(), food.getFat100g()));
    }

    private static Double forGrams(Double per100g, double grams) {
        return per100g == null ? null : round(per100g * grams / 100);
    }

    private static double value(Double d) {
        return d == null ? 0 : d;
    }

    private static double round(double d) {
        return Math.round(d * 10) / 10.0;
    }
}
