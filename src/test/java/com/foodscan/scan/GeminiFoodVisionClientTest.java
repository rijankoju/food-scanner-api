package com.foodscan.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GeminiFoodVisionClientTest {

    private static final String URL = "https://gemini.test/v1beta/models/gemini-test:generateContent";
    private static final byte[] IMAGE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private MockRestServiceServer server;
    private GeminiFoodVisionClient client;

    @BeforeEach
    void setUp() {
        client = clientWithKey("test-key");
    }

    private GeminiFoodVisionClient clientWithKey(String key) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        return new GeminiFoodVisionClient(new VisionProperties.Gemini(key, "gemini-test", "https://gemini.test", 8192),
                builder.build(), JsonMapper.builder().build());
    }

    @Test
    void sendsImageWithSchemaAndParsesAnswer() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(VisionPrompt.SYSTEM))
                .andExpect(jsonPath("$.contents[0].parts[1].inlineData.mimeType").value("image/jpeg"))
                .andExpect(jsonPath("$.contents[0].parts[1].inlineData.data").value("/9j/"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
                .andRespond(withSuccess("""
                        {"candidates": [{"finishReason": "STOP", "content": {"role": "model", "parts": [
                            {"text": "Looking at the plate...", "thought": true},
                            {"text": "{\\"mealName\\": \\"Rice bowl\\", \\"notes\\": \\"\\", \\"items\\": [{\\"name\\": \\"white rice\\", \\"databaseQuery\\": \\"Rice, white, cooked\\", \\"cookingMethod\\": \\"boiled\\", \\"hiddenAddition\\": false, \\"estimatedGrams\\": 180, \\"gramsLow\\": 150, \\"gramsHigh\\": 220, \\"confidence\\": 0.9, \\"kcalPer100g\\": 130, \\"proteinPer100g\\": 2.7, \\"carbsPer100g\\": 28, \\"fatPer100g\\": 0.3, \\"extraField\\": 1}]}"}
                        ]}}],
                         "usageMetadata": {"promptTokenCount": 1800, "candidatesTokenCount": 250, "thoughtsTokenCount": 400},
                         "modelVersion": "gemini-test-001"}
                        """, MediaType.APPLICATION_JSON));

        FoodVisionClient.Result result = client.analyze(IMAGE, "image/jpeg");

        assertThat(result.model()).isEqualTo("gemini-test-001");
        assertThat(result.inputTokens()).isEqualTo(1800);
        assertThat(result.outputTokens()).isEqualTo(650);
        assertThat(result.analysis().mealName()).isEqualTo("Rice bowl");
        MealAnalysis.Item rice = result.analysis().items().getFirst();
        assertThat(rice.databaseQuery()).isEqualTo("Rice, white, cooked");
        assertThat(rice.estimatedGrams()).isEqualTo(180);
        assertThat(rice.carbsPer100g()).isEqualTo(28);
        server.verify();
    }

    @Test
    void rateLimitGivesFriendlyMessage() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"error\": {\"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\"}}"));

        assertThatThrownBy(() -> client.analyze(IMAGE, "image/jpeg"))
                .isInstanceOf(FoodVisionClient.VisionException.class)
                .hasMessageContaining("free AI quota");
    }

    @Test
    void serverErrorIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.analyze(IMAGE, "image/jpeg"))
                .hasMessageContaining("unavailable");
    }

    @Test
    void blockedAndTruncatedAnswersFail() {
        assertThatThrownBy(() -> client.parse("""
                {"promptFeedback": {"blockReason": "IMAGE_SAFETY"}}"""))
                .hasMessageContaining("couldn't be analyzed");
        assertThatThrownBy(() -> client.parse("""
                {"candidates": [{"finishReason": "SAFETY", "content": {"parts": []}}]}"""))
                .hasMessageContaining("couldn't be analyzed");
        assertThatThrownBy(() -> client.parse("""
                {"candidates": [{"finishReason": "MAX_TOKENS", "content": {"parts": [{"text": "{\\"mealN"}]}}]}"""))
                .hasMessageContaining("cut short");
        assertThatThrownBy(() -> client.parse("""
                {"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": "not json"}]}}]}"""))
                .hasMessageContaining("malformed");
        assertThatThrownBy(() -> client.parse("""
                {"candidates": [{"finishReason": "STOP", "content": {"parts": []}}]}"""))
                .hasMessageContaining("empty");
    }

    @Test
    void missingApiKeyFailsWithoutCallingGemini() {
        GeminiFoodVisionClient noKey = clientWithKey("");

        assertThatThrownBy(() -> noKey.analyze(IMAGE, "image/jpeg"))
                .hasMessageContaining("isn't set up");
        server.verify();
    }

    @Test
    @SuppressWarnings("unchecked")
    void schemaMatchesMealAnalysisRecords() {
        Map<String, Object> schema = GeminiFoodVisionClient.MEAL_ANALYSIS_SCHEMA;
        assertThat((List<String>) schema.get("required")).containsExactly(names(MealAnalysis.class));

        Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("items");
        Map<String, Object> item = (Map<String, Object>) items.get("items");
        assertThat((List<String>) item.get("required")).containsExactly(names(MealAnalysis.Item.class));
        assertThat(((Map<String, Object>) item.get("properties")).keySet())
                .containsExactlyInAnyOrder(names(MealAnalysis.Item.class));
    }

    private static String[] names(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
    }
}
