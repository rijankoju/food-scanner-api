package com.foodscan.food;

import java.util.List;

public record FoodDetail(
        long id,
        String source,
        String sourceRef,
        String dataType,
        String name,
        String brand,
        String category,
        Nutrients per100g,
        Double fiber100g,
        Double sugar100g,
        Double sodiumMg100g,
        String energyBasis,
        List<Portion> portions) {

    public record Portion(String label, double grams) {
    }

    static FoodDetail from(Food food) {
        return new FoodDetail(
                food.getId(),
                food.getSource(),
                food.getSourceRef(),
                food.getDataType(),
                food.getName(),
                food.getBrand(),
                food.getCategory(),
                Nutrients.of(food.getKcal100g(), food.getProtein100g(), food.getCarbs100g(), food.getFat100g()),
                food.getFiber100g() == null ? null : food.getFiber100g().doubleValue(),
                food.getSugar100g() == null ? null : food.getSugar100g().doubleValue(),
                food.getSodiumMg100g() == null ? null : food.getSodiumMg100g().doubleValue(),
                food.getEnergyBasis(),
                food.getPortions().stream()
                        .map(p -> new Portion(p.getLabel(), p.getGrams().doubleValue()))
                        .toList());
    }
}
