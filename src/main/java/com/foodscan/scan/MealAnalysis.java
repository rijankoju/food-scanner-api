package com.foodscan.scan;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * What the vision model returns for a meal photo. The JSON schema sent to
 * the model is derived from these records, including the descriptions.
 */
public record MealAnalysis(
        @JsonPropertyDescription("Short name for the whole meal, e.g. 'Chicken rice bowl'. Empty if no food is visible.")
        String mealName,

        @JsonPropertyDescription("Each distinct food or drink, plus hidden additions, in order of size.")
        List<Item> items,

        @JsonPropertyDescription("Anything that limits the estimate (blurry photo, partly eaten, not food). Empty if none.")
        String notes) {

    public record Item(
            @JsonPropertyDescription("Plain name as a user would say it, e.g. 'grilled chicken breast'.")
            String name,

            @JsonPropertyDescription("Search phrase in USDA FNDDS style: food first, then form and preparation, "
                    + "e.g. 'Chicken breast, grilled, skin not eaten' or 'Rice, white, cooked, no added fat'.")
            String databaseQuery,

            @JsonPropertyDescription("How it was cooked, e.g. 'grilled', 'fried', 'raw', 'boiled'. 'unknown' if unclear.")
            String cookingMethod,

            @JsonPropertyDescription("True for something not directly visible but almost certainly present, "
                    + "such as cooking oil, butter, dressing or sugar in a drink.")
            boolean hiddenAddition,

            @JsonPropertyDescription("Best estimate of the edible weight in grams.")
            double estimatedGrams,

            @JsonPropertyDescription("Low end of a plausible weight range in grams.")
            double gramsLow,

            @JsonPropertyDescription("High end of a plausible weight range in grams.")
            double gramsHigh,

            @JsonPropertyDescription("Confidence from 0 to 1 that the food is identified correctly.")
            double confidence,

            @JsonPropertyDescription("Estimated kcal per 100 g as prepared. Used only if no database match is found.")
            double kcalPer100g,

            @JsonPropertyDescription("Estimated protein grams per 100 g as prepared.")
            double proteinPer100g,

            @JsonPropertyDescription("Estimated carbohydrate grams per 100 g as prepared.")
            double carbsPer100g,

            @JsonPropertyDescription("Estimated fat grams per 100 g as prepared.")
            double fatPer100g) {
    }
}
