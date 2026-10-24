package com.foodscan.usda;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Maps one food object from the FoodData Central JSON downloads (Foundation,
 * SR Legacy and Survey/FNDDS share this shape) to a {@link UsdaFood}.
 */
final class UsdaFoodMapper {

    // FoodData Central nutrient numbers. Foundation foods often report energy
    // only as Atwater specific (958) or general (957) factors instead of 208.
    private static final String ENERGY_KCAL = "208";
    private static final String ENERGY_ATWATER_SPECIFIC = "958";
    private static final String ENERGY_ATWATER_GENERAL = "957";
    private static final String PROTEIN = "203";
    private static final String FAT = "204";
    private static final String FAT_NLEA = "298";
    private static final String CARBS_BY_DIFFERENCE = "205";
    private static final String CARBS_BY_SUMMATION = "205.2";
    private static final String FIBER = "291";
    private static final String SUGARS = "269";
    private static final String SUGARS_NLEA = "269.3";
    private static final String SODIUM_MG = "307";

    private UsdaFoodMapper() {
    }

    static UsdaFood map(JsonNode food) {
        Map<String, Double> amounts = new HashMap<>();
        for (JsonNode n : food.path("foodNutrients")) {
            JsonNode amount = n.path("amount");
            String number = n.path("nutrient").path("number").asString("");
            if (amount.isNumber() && !number.isEmpty()) {
                amounts.putIfAbsent(number, amount.asDouble());
            }
        }

        Double protein = amounts.get(PROTEIN);
        Double fat = first(amounts, FAT, FAT_NLEA);
        Double carbs = first(amounts, CARBS_BY_DIFFERENCE, CARBS_BY_SUMMATION);

        Double kcal;
        String energyBasis;
        if (amounts.containsKey(ENERGY_KCAL)) {
            kcal = amounts.get(ENERGY_KCAL);
            energyBasis = "reported";
        } else if (amounts.containsKey(ENERGY_ATWATER_SPECIFIC)) {
            kcal = amounts.get(ENERGY_ATWATER_SPECIFIC);
            energyBasis = "atwater_specific";
        } else if (amounts.containsKey(ENERGY_ATWATER_GENERAL)) {
            kcal = amounts.get(ENERGY_ATWATER_GENERAL);
            energyBasis = "atwater_general";
        } else if (protein != null && fat != null && carbs != null) {
            kcal = 4 * protein + 4 * carbs + 9 * fat;
            energyBasis = "computed";
        } else {
            kcal = null;
            energyBasis = null;
        }

        return new UsdaFood(
                food.path("fdcId").asString(),
                food.path("dataType").asString(null),
                food.path("description").asString().strip(),
                category(food),
                round(kcal),
                round(protein),
                round(carbs),
                round(fat),
                round(amounts.get(FIBER)),
                round(first(amounts, SUGARS, SUGARS_NLEA)),
                round(amounts.get(SODIUM_MG)),
                energyBasis,
                portions(food));
    }

    private static String category(JsonNode food) {
        String category = food.path("foodCategory").path("description").asString(null);
        if (category == null) {
            category = food.path("wweiaFoodCategory").path("wweiaFoodCategoryDescription").asString(null);
        }
        return category;
    }

    private static List<UsdaFood.Portion> portions(JsonNode food) {
        List<UsdaFood.Portion> portions = new ArrayList<>();
        for (JsonNode p : food.path("foodPortions")) {
            double grams = p.path("gramWeight").asDouble(0);
            String label = portionLabel(p);
            if (grams > 0 && !label.isBlank()) {
                portions.add(new UsdaFood.Portion(label, round(grams)));
            }
        }
        return portions;
    }

    /**
     * FNDDS portions carry a ready-made description ("1 cup"). Foundation and
     * SR Legacy portions are amount + unit + modifier, where the unit is often
     * "undetermined" and the modifier holds the real meaning ("1 serving").
     */
    private static String portionLabel(JsonNode p) {
        String description = p.path("portionDescription").asString("").strip();
        if (!description.isEmpty()) {
            return description;
        }
        double amount = p.path("amount").asDouble(p.path("value").asDouble(1));
        String unit = p.path("measureUnit").path("name").asString("");
        String modifier = p.path("modifier").asString("").strip();
        if (unit.equals("undetermined")) {
            unit = "";
        } else if (unit.equals("RACC")) {
            unit = "serving (RACC)";
        }
        return (formatAmount(amount) + " " + unit + " " + modifier).strip().replaceAll("\\s+", " ");
    }

    private static String formatAmount(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private static Double first(Map<String, Double> amounts, String preferred, String fallback) {
        Double value = amounts.get(preferred);
        return value != null ? value : amounts.get(fallback);
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 100) / 100.0;
    }
}
