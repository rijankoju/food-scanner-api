package com.foodscan.food;

/** A search hit. {@code per100g} holds the macros per 100 g. */
public record FoodSummary(
        long id,
        String name,
        String dataType,
        String category,
        Nutrients per100g,
        double score) {

    static FoodSummary from(FoodRepository.SearchRow row) {
        return new FoodSummary(
                row.getId(),
                row.getName(),
                row.getDataType(),
                row.getCategory(),
                Nutrients.of(row.getKcal100g(), row.getProtein100g(), row.getCarbs100g(), row.getFat100g()),
                row.getScore());
    }
}
