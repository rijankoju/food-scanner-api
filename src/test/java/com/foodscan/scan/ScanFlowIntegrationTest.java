package com.foodscan.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodscan.TestcontainersConfiguration;
import com.foodscan.food.Food;
import com.foodscan.food.FoodRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Upload -> scan -> review flow with a fake vision model. */
@Import({TestcontainersConfiguration.class, ScanFlowIntegrationTest.FakeVision.class})
@SpringBootTest(properties = "foodscan.storage.local-dir=target/test-uploads")
@AutoConfigureMockMvc
class ScanFlowIntegrationTest {

    /** Smallest byte sequence our content sniffing accepts as a JPEG. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    /** What the fake model returns next; a null analysis makes it fail. */
    static final AtomicReference<MealAnalysis> NEXT = new AtomicReference<>();

    @TestConfiguration
    static class FakeVision {
        @Bean
        @Primary
        FoodVisionClient fakeVisionClient() {
            return (image, contentType) -> {
                MealAnalysis analysis = NEXT.get();
                if (analysis == null) {
                    throw new FoodVisionClient.VisionException("This photo couldn't be analyzed.");
                }
                return new FoodVisionClient.Result(analysis, "fake-model", 1500, 400);
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FoodRepository foods;

    private String unique;

    @BeforeEach
    void seedFoods() {
        unique = "Qx" + UUID.randomUUID().toString().substring(0, 6);
        food(unique + " chicken breast, grilled", "165", "31", "0", "3.6");
        food(unique + " chicken breast, fried", "260", "28", "8", "13");
        food(unique + " rice, white, cooked", "130", "2.7", "28", "0.3");
    }

    @Test
    void scanMatchesFlagsAndAcceptsCorrections() throws Exception {
        NEXT.set(new MealAnalysis("Chicken and rice", List.of(
                new MealAnalysis.Item("grilled chicken", unique + " chicken breast grilled", "grilled", false,
                        150, 120, 180, 0.9, 170, 31, 0, 4),
                new MealAnalysis.Item("white rice", unique + " rice white cooked", "boiled", false,
                        200, 160, 250, 0.85, 130, 2.7, 28, 0.3),
                new MealAnalysis.Item("mystery sauce", "Zzvorpt blenquist", "unknown", false,
                        30, 20, 45, 0.4, 100, 1, 10, 6)), ""));

        String token = register();
        JsonNode scan = scan(token);
        assertThat(scan.path("status").asString()).isEqualTo("ready");
        assertThat(scan.path("mealName").asString()).isEqualTo("Chicken and rice");

        JsonNode items = scan.path("items");
        assertThat(items).hasSize(3);

        JsonNode chicken = items.get(0);
        assertThat(chicken.path("source").asString()).isEqualTo("db");
        assertThat(chicken.path("food").path("name").asString()).isEqualTo(unique + " chicken breast, grilled");
        assertThat(chicken.path("nutrients").path("kcal").asDouble()).isEqualTo(247.5);
        assertThat(chicken.path("flags")).isEmpty();
        assertThat(chicken.path("alternatives").toString()).contains("chicken breast, fried");

        JsonNode sauce = items.get(2);
        assertThat(sauce.path("source").asString()).isEqualTo("ai");
        assertThat(sauce.path("nutrients").path("kcal").asDouble()).isEqualTo(30.0);
        assertThat(flags(sauce)).contains("no_db_match", "low_confidence");

        // 247.5 + 260 + 30
        assertThat(scan.path("totals").path("kcal").asDouble()).isEqualTo(537.5);

        long scanId = scan.path("id").asLong();
        long chickenItem = chicken.path("id").asLong();

        JsonNode corrected = call(patch("/api/scans/" + scanId + "/items/" + chickenItem), token,
                "{\"grams\": 100}", status().isOk());
        assertThat(corrected.path("nutrients").path("kcal").asDouble()).isEqualTo(165.0);
        assertThat(corrected.path("estimatedGrams").asDouble()).isEqualTo(150.0);

        long friedId = foods.search(unique + " chicken breast fried", 1).getFirst().getId();
        corrected = call(patch("/api/scans/" + scanId + "/items/" + sauce.path("id").asLong()), token,
                "{\"foodId\": " + friedId + "}", status().isOk());
        assertThat(corrected.path("source").asString()).isEqualTo("db");
        assertThat(flags(corrected)).doesNotContain("no_db_match");

        call(delete("/api/scans/" + scanId + "/items/" + items.get(1).path("id").asLong()), token, null,
                status().isNoContent());
        JsonNode after = call(get("/api/scans/" + scanId), token, null, status().isOk());
        assertThat(after.path("items")).hasSize(2);
        // 165 (100 g grilled) + 78 (30 g fried)
        assertThat(after.path("totals").path("kcal").asDouble()).isEqualTo(243.0);

        // The unmatched item's AI-estimate food can be logged to the diary like any other food
        call(post("/api/diary/entries"), token, """
                {"date": "2026-06-01", "mealType": "dinner", "foodId": %d, "grams": 100, "scanItemId": %d}
                """.formatted(chicken.path("food").path("id").asLong(), chickenItem), status().isCreated());

        String other = register();
        call(get("/api/scans/" + scanId), other, null, status().isNotFound());
        // The AI-estimate food is private to the user who scanned it
        long aiFoodId = sauce.path("food").path("id").asLong();
        call(get("/api/foods/" + aiFoodId), token, null, status().isOk());
        call(get("/api/foods/" + aiFoodId), other, null, status().isNotFound());
        call(patch("/api/scans/" + scanId + "/items/" + chickenItem), other, "{\"grams\": 5}", status().isNotFound());
    }

    @Test
    void modelFailureMarksScanFailed() throws Exception {
        NEXT.set(null);
        JsonNode scan = scan(register());
        assertThat(scan.path("status").asString()).isEqualTo("failed");
        assertThat(scan.path("error").asString()).isEqualTo("This photo couldn't be analyzed.");
    }

    @Test
    void rejectsForeignKeysAndNonImages() throws Exception {
        String token = register();
        String otherToken = register();
        JsonNode upload = call(post("/api/uploads/presign"), otherToken, "{\"contentType\": \"image/jpeg\"}",
                status().isOk());

        // Someone else's key
        call(post("/api/scans"), token, "{\"imageKey\": \"" + upload.path("key").asString() + "\"}",
                status().isForbidden());
        mvc.perform(put(localPath(upload)).header("Authorization", "Bearer " + token).content(JPEG))
                .andExpect(status().isForbidden());

        // Not an image
        mvc.perform(put(localPath(upload)).header("Authorization", "Bearer " + otherToken)
                        .content("<html>hi</html>".getBytes()))
                .andExpect(status().isUnsupportedMediaType());
        call(post("/api/uploads/presign"), token, "{\"contentType\": \"application/pdf\"}",
                status().isUnsupportedMediaType());
    }

    @Test
    void profilePhotoUploadServeAndRemove() throws Exception {
        String token = register();
        JsonNode upload = call(post("/api/uploads/presign"), token,
                "{\"contentType\": \"image/jpeg\", \"kind\": \"avatars\"}", status().isOk());
        String key = upload.path("key").asString();
        assertThat(key).startsWith("avatars/");

        // Setting it before the bytes exist fails; a scan can't use an avatar key.
        call(put("/api/me/avatar"), token, "{\"imageKey\": \"" + key + "\"}", status().isUnprocessableContent());
        mvc.perform(put(localPath(upload)).header("Authorization", "Bearer " + token).content(JPEG))
                .andExpect(status().isNoContent());
        call(post("/api/scans"), token, "{\"imageKey\": \"" + key + "\"}", status().isForbidden());

        JsonNode me = call(put("/api/me/avatar"), token, "{\"imageKey\": \"" + key + "\"}", status().isOk());
        assertThat(me.path("avatarUrl").asString()).startsWith("/api/me/avatar?v=");
        byte[] served = mvc.perform(get("/api/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(JPEG);

        // Another user can't claim this upload.
        call(put("/api/me/avatar"), register(), "{\"imageKey\": \"" + key + "\"}", status().isForbidden());

        me = call(delete("/api/me/avatar"), token, null, status().isOk());
        assertThat(me.path("avatarUrl").isNull()).isTrue();
        mvc.perform(get("/api/me/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /** Presign, upload, create the scan, then poll until it finishes. */
    private JsonNode scan(String token) throws Exception {
        JsonNode upload = call(post("/api/uploads/presign"), token, "{\"contentType\": \"image/jpeg\"}",
                status().isOk());
        assertThat(upload.path("requiresAuth").asBoolean()).isTrue();
        mvc.perform(put(localPath(upload)).header("Authorization", "Bearer " + token).content(JPEG))
                .andExpect(status().isNoContent());

        JsonNode created = call(post("/api/scans"), token,
                "{\"imageKey\": \"" + upload.path("key").asString() + "\"}", status().isAccepted());
        assertThat(created.path("status").asString()).isEqualTo("processing");

        long id = created.path("id").asLong();
        for (int i = 0; i < 100; i++) {
            JsonNode scan = call(get("/api/scans/" + id), token, null, status().isOk());
            if (!scan.path("status").asString().equals("processing")) {
                return scan;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Scan did not finish within 10 s");
    }

    private static String localPath(JsonNode upload) {
        String url = upload.path("url").asString();
        return url.substring(url.indexOf("/api/"));
    }

    private static List<String> flags(JsonNode item) {
        return item.path("flags").valueStream().map(JsonNode::asString).toList();
    }

    private void food(String name, String kcal, String protein, String carbs, String fat) {
        Food food = new Food("usda", "test-" + UUID.randomUUID());
        food.setName(name);
        food.setKcal100g(new BigDecimal(kcal));
        food.setProtein100g(new BigDecimal(protein));
        food.setCarbs100g(new BigDecimal(carbs));
        food.setFat100g(new BigDecimal(fat));
        foods.save(food);
    }

    private String register() throws Exception {
        String body = "{\"email\": \"%s@example.com\", \"password\": \"password123\"}".formatted(UUID.randomUUID());
        return call(post("/api/auth/register"), null, body, status().isCreated()).path("accessToken").asString();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, String body, ResultMatcher expected)
            throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mvc.perform(request).andExpect(expected).andReturn().getResponse().getContentAsString();
        return response.isEmpty() ? json.missingNode() : json.readTree(response);
    }
}
