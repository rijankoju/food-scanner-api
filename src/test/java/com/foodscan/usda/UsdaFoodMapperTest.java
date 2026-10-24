package com.foodscan.usda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class UsdaFoodMapperTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void usesReportedEnergyAndSrLegacyPortions() {
        UsdaFood food = UsdaFoodMapper.map(json("""
                {"fdcId": 167512, "dataType": "SR Legacy", "description": " Biscuits, refrigerated dough ",
                 "foodCategory": {"description": "Baked Products"},
                 "foodNutrients": [
                   {"nutrient": {"number": "208"}, "amount": 307},
                   {"nutrient": {"number": "203"}, "amount": 5.88},
                   {"nutrient": {"number": "204"}, "amount": 13.2},
                   {"nutrient": {"number": "205"}, "amount": 41.2},
                   {"nutrient": {"number": "307"}, "amount": 965}
                 ],
                 "foodPortions": [
                   {"amount": 1.0, "measureUnit": {"name": "undetermined"}, "modifier": "serving", "gramWeight": 34.0},
                   {"amount": 0, "measureUnit": {"name": "cup"}, "gramWeight": 0}
                 ]}
                """));

        assertThat(food.fdcId()).isEqualTo("167512");
        assertThat(food.name()).isEqualTo("Biscuits, refrigerated dough");
        assertThat(food.category()).isEqualTo("Baked Products");
        assertThat(food.kcal()).isEqualTo(307.0);
        assertThat(food.energyBasis()).isEqualTo("reported");
        assertThat(food.sodiumMg()).isEqualTo(965.0);
        assertThat(food.portions()).containsExactly(new UsdaFood.Portion("1 serving", 34.0));
    }

    @Test
    void fallsBackToAtwaterEnergyForFoundationFoods() {
        UsdaFood food = UsdaFoodMapper.map(json("""
                {"fdcId": 321358, "dataType": "Foundation", "description": "Hummus, commercial",
                 "foodNutrients": [
                   {"nutrient": {"number": "957"}, "amount": 240},
                   {"nutrient": {"number": "958"}, "amount": 229},
                   {"nutrient": {"number": "203"}, "amount": 7.35},
                   {"nutrient": {"number": "334"}}
                 ],
                 "foodPortions": [
                   {"amount": 2.0, "measureUnit": {"name": "tablespoon"}, "modifier": "", "gramWeight": 33.9}
                 ]}
                """));

        assertThat(food.kcal()).isEqualTo(229.0);
        assertThat(food.energyBasis()).isEqualTo("atwater_specific");
        assertThat(food.portions()).containsExactly(new UsdaFood.Portion("2 tablespoon", 33.9));
    }

    @Test
    void computesEnergyFromMacrosAndReadsFnddsPortions() {
        UsdaFood food = UsdaFoodMapper.map(json("""
                {"fdcId": 2705385, "dataType": "Survey (FNDDS)", "description": "Milk, fat free (skim)",
                 "wweiaFoodCategory": {"wweiaFoodCategoryDescription": "Milk, nonfat"},
                 "foodNutrients": [
                   {"nutrient": {"number": "203"}, "amount": 3.43},
                   {"nutrient": {"number": "204"}, "amount": 0.08},
                   {"nutrient": {"number": "205"}, "amount": 4.92}
                 ],
                 "foodPortions": [
                   {"measureUnit": {"name": "undetermined"}, "modifier": "10205", "gramWeight": 244, "portionDescription": "1 cup"}
                 ]}
                """));

        assertThat(food.kcal()).isEqualTo(34.12);
        assertThat(food.energyBasis()).isEqualTo("computed");
        assertThat(food.category()).isEqualTo("Milk, nonfat");
        assertThat(food.portions()).containsExactly(new UsdaFood.Portion("1 cup", 244.0));
    }

    @Test
    void foodWithoutNutrientsHasNoMacros() {
        UsdaFood food = UsdaFoodMapper.map(json("""
                {"fdcId": 1, "description": "Milk, human", "foodNutrients": [], "foodPortions": []}
                """));

        assertThat(food.hasMacros()).isFalse();
    }

    private JsonNode json(String json) {
        return mapper.readTree(json);
    }
}
