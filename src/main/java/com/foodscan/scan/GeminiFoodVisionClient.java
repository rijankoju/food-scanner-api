package com.foodscan.scan;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * {@link FoodVisionClient} backed by the Gemini API ({@code generateContent})
 * with a JSON response schema. Works on the free tier, which is rate limited.
 */
class GeminiFoodVisionClient implements FoodVisionClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiFoodVisionClient.class);

    /** Finish reasons meaning the model declined or was stopped by a filter. */
    private static final Set<String> BLOCKED = Set.of(
            "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY", "OTHER");

    private final VisionProperties.Gemini properties;
    private final RestClient restClient;
    private final ObjectMapper json;
    private final ObjectReader analysisReader;

    GeminiFoodVisionClient(VisionProperties.Gemini properties, RestClient restClient, ObjectMapper json) {
        this.properties = properties;
        this.restClient = restClient;
        this.json = json;
        this.analysisReader = json.readerFor(MealAnalysis.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Result analyze(byte[] image, String contentType) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.error("GEMINI_API_KEY is not set; photo analysis is unavailable");
            throw new VisionException("Photo analysis isn't set up on the server yet.");
        }

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request(image, contentType))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                log.warn("Gemini rate limit hit: {}", e.getResponseBodyAsString());
                throw new VisionException("The free AI quota is used up for now. Wait a minute and try again.");
            }
            log.warn("Gemini request failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new VisionException("The food analysis service is unavailable. Please try again.");
        } catch (RestClientException e) {
            log.warn("Gemini request failed", e);
            throw new VisionException("The food analysis service is unavailable. Please try again.");
        }
        return parse(responseBody);
    }

    private Map<String, Object> request(byte[] image, String contentType) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", VisionPrompt.SYSTEM))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(
                                Map.of("text", VisionPrompt.USER),
                                Map.of("inlineData", Map.of(
                                        "mimeType", contentType,
                                        "data", Base64.getEncoder().encodeToString(image)))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", MEAL_ANALYSIS_SCHEMA,
                        "maxOutputTokens", properties.maxOutputTokens()));
    }

    Result parse(String responseBody) {
        JsonNode root;
        try {
            root = json.readTree(responseBody);
        } catch (JacksonException e) {
            throw new VisionException("The analysis came back malformed. Please try again.");
        }

        if (!root.path("promptFeedback").path("blockReason").isMissingNode()) {
            log.info("Gemini blocked the prompt: {}", root.path("promptFeedback"));
            throw new VisionException("This photo couldn't be analyzed. Try another photo of the meal.");
        }
        JsonNode candidate = root.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asString("");
        if (finishReason.equals("MAX_TOKENS")) {
            throw new VisionException("The analysis was cut short. Try a photo with fewer items.");
        }
        if (BLOCKED.contains(finishReason)) {
            log.info("Gemini stopped with finishReason={}", finishReason);
            throw new VisionException("This photo couldn't be analyzed. Try another photo of the meal.");
        }

        // Skip "thought" parts; the answer is the remaining text.
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asString(""));
            }
        }
        if (text.isEmpty()) {
            throw new VisionException("The analysis came back empty. Please try again.");
        }

        MealAnalysis analysis;
        try {
            analysis = analysisReader.readValue(text.toString());
        } catch (JacksonException e) {
            log.warn("Gemini returned JSON that doesn't match the schema: {}", text);
            throw new VisionException("The analysis came back malformed. Please try again.");
        }

        JsonNode usage = root.path("usageMetadata");
        long outputTokens = usage.path("candidatesTokenCount").asLong(0) + usage.path("thoughtsTokenCount").asLong(0);
        String model = root.path("modelVersion").asString(properties.model());
        log.info("Vision analysis: model={} items={} inputTokens={} outputTokens={}", model,
                analysis.items() == null ? 0 : analysis.items().size(),
                usage.path("promptTokenCount").asLong(0), outputTokens);
        return new Result(analysis, model, usage.path("promptTokenCount").asLong(0), outputTokens);
    }

    /**
     * Gemini's OpenAPI-style response schema for {@link MealAnalysis}. Property
     * names must match the record components; GeminiFoodVisionClientTest checks this.
     */
    static final Map<String, Object> MEAL_ANALYSIS_SCHEMA = object(
            List.of("mealName", "items", "notes"),
            Map.of(
                    "mealName", string("Short name for the whole meal, e.g. 'Chicken rice bowl'. Empty if no food is visible."),
                    "items", Map.of(
                            "type", "ARRAY",
                            "description", "Each distinct food or drink, plus hidden additions, in order of size.",
                            "items", object(
                                    List.of("name", "databaseQuery", "cookingMethod", "hiddenAddition",
                                            "estimatedGrams", "gramsLow", "gramsHigh", "confidence",
                                            "kcalPer100g", "proteinPer100g", "carbsPer100g", "fatPer100g"),
                                    Map.ofEntries(
                                            Map.entry("name", string("Plain name as a user would say it, e.g. 'grilled chicken breast'.")),
                                            Map.entry("databaseQuery", string("Search phrase in USDA FNDDS style: food first, then form and "
                                                    + "preparation, e.g. 'Chicken breast, grilled, skin not eaten'.")),
                                            Map.entry("cookingMethod", string("How it was cooked, e.g. 'grilled', 'fried', 'raw'. 'unknown' if unclear.")),
                                            Map.entry("hiddenAddition", Map.of("type", "BOOLEAN", "description",
                                                    "True for something not directly visible but almost certainly present, "
                                                            + "such as cooking oil, butter, dressing or sugar in a drink.")),
                                            Map.entry("estimatedGrams", number("Best estimate of the edible weight in grams.")),
                                            Map.entry("gramsLow", number("Low end of a plausible weight range in grams.")),
                                            Map.entry("gramsHigh", number("High end of a plausible weight range in grams.")),
                                            Map.entry("confidence", number("Confidence from 0 to 1 that the food is identified correctly.")),
                                            Map.entry("kcalPer100g", number("Estimated kcal per 100 g as prepared.")),
                                            Map.entry("proteinPer100g", number("Estimated protein grams per 100 g as prepared.")),
                                            Map.entry("carbsPer100g", number("Estimated carbohydrate grams per 100 g as prepared.")),
                                            Map.entry("fatPer100g", number("Estimated fat grams per 100 g as prepared."))))),
                    "notes", string("Anything that limits the estimate (blurry photo, partly eaten, not food). Empty if none.")));

    private static Map<String, Object> object(List<String> order, Map<String, Object> properties) {
        return Map.of("type", "OBJECT", "properties", properties, "required", order, "propertyOrdering", order);
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "STRING", "description", description);
    }

    private static Map<String, Object> number(String description) {
        return Map.of("type", "NUMBER", "description", description);
    }
}
