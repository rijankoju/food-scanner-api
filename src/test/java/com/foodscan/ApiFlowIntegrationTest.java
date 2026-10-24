package com.foodscan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodscan.food.Food;
import com.foodscan.food.FoodRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises the week-2 API end to end against a real Postgres container. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    private static final String DATE = "2026-06-01";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired FoodRepository foods;

    private long chickenId;

    @BeforeEach
    void createFood() {
        Food chicken = new Food("usda", "test-" + UUID.randomUUID());
        chicken.setName("Chicken breast, grilled");
        chicken.setKcal100g(new BigDecimal("165"));
        chicken.setProtein100g(new BigDecimal("31"));
        chicken.setCarbs100g(BigDecimal.ZERO);
        chicken.setFat100g(new BigDecimal("3.6"));
        chickenId = foods.save(chicken).getId();
    }

    @Test
    void protectedEndpointsRequireToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/foods/search").param("q", "rice")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerLoginAndDuplicates() throws Exception {
        String email = uniqueEmail();
        call(post("/api/auth/register"), null, credentials(email, "correct horse 1"), status().isCreated());
        call(post("/api/auth/register"), null, credentials(email.toUpperCase(), "another pass"), status().isConflict());
        call(post("/api/auth/login"), null, credentials(email, "wrong password"), status().isUnauthorized());
        call(post("/api/auth/login"), null, credentials("nobody-" + email, "whatever1"), status().isUnauthorized());
        JsonNode tokens = call(post("/api/auth/login"), null, credentials(email, "correct horse 1"), status().isOk());
        assertThat(tokens.path("accessToken").asString()).isNotBlank();
        assertThat(tokens.path("expiresIn").asLong()).isEqualTo(900);
        call(post("/api/auth/register"), null, credentials("not-an-email", "password123"), status().isBadRequest());
    }

    @Test
    void profileGoalsAndDiary() throws Exception {
        String token = register();

        call(get("/api/me/goals"), token, null, status().isNotFound());

        JsonNode me = call(put("/api/me"), token, """
                {"sex": "male", "birthDate": "1996-01-01", "heightCm": 180, "weightKg": 80,
                 "activityLevel": "moderate", "goalType": "maintain"}
                """, status().isOk());
        assertThat(me.path("latestWeightKg").decimalValue()).isEqualByComparingTo("80");
        int goalKcal = me.path("goal").path("kcal").asInt();
        assertThat(goalKcal).isBetween(2000, 3500);

        // 150 g at 165 kcal / 31 g protein per 100 g
        JsonNode entry = call(post("/api/diary/entries"), token, """
                {"date": "%s", "mealType": "lunch", "foodId": %d, "grams": 150}
                """.formatted(DATE, chickenId), status().isCreated());
        assertThat(entry.path("kcal").decimalValue()).isEqualByComparingTo("247.5");
        assertThat(entry.path("proteinG").decimalValue()).isEqualByComparingTo("46.5");
        long entryId = entry.path("id").asLong();

        JsonNode fetched = call(get("/api/diary/entries/" + entryId), token, null, status().isOk());
        assertThat(fetched.path("foodId").asLong()).isEqualTo(chickenId);

        JsonNode recent = call(get("/api/diary/recent-foods"), token, null, status().isOk());
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).path("id").asLong()).isEqualTo(chickenId);
        assertThat(recent.get(0).path("lastGrams").decimalValue()).isEqualByComparingTo("150");

        JsonNode day = call(get("/api/diary").param("date", DATE), token, null, status().isOk());
        assertThat(day.path("totals").path("kcal").decimalValue()).isEqualByComparingTo("247.5");
        assertThat(day.path("meals")).hasSize(4);
        assertThat(day.path("meals").get(1).path("mealType").asString()).isEqualTo("lunch");
        assertThat(day.path("meals").get(1).path("entries").get(0).path("foodName").asString())
                .isEqualTo("Chicken breast, grilled");

        // Doubling grams doubles the snapshot
        JsonNode updated = call(put("/api/diary/entries/" + entryId), token, """
                {"grams": 300, "mealType": "dinner"}
                """, status().isOk());
        assertThat(updated.path("kcal").decimalValue()).isEqualByComparingTo("495.0");
        assertThat(updated.path("mealType").asString()).isEqualTo("dinner");

        // Another user can't see or change it
        String otherToken = register();
        call(put("/api/diary/entries/" + entryId), otherToken, "{\"grams\": 1}", status().isNotFound());
        call(delete("/api/diary/entries/" + entryId), otherToken, null, status().isNotFound());
        call(get("/api/diary/entries/" + entryId), otherToken, null, status().isNotFound());
        assertThat(call(get("/api/diary/recent-foods"), otherToken, null, status().isOk())).isEmpty();

        JsonNode stats = call(get("/api/stats").param("from", "2026-05-25").param("to", DATE), token, null,
                status().isOk());
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).path("kcal").decimalValue()).isEqualByComparingTo("495.0");

        call(delete("/api/diary/entries/" + entryId), token, null, status().isNoContent());
        day = call(get("/api/diary").param("date", DATE), token, null, status().isOk());
        assertThat(day.path("totals").path("kcal").decimalValue()).isEqualByComparingTo("0");
        assertThat(day.path("remaining").path("kcal").asInt()).isEqualTo(goalKcal);

        call(post("/api/diary/entries"), token, """
                {"date": "%s", "mealType": "lunch", "foodId": %d, "grams": -5}
                """.formatted(DATE, chickenId), status().isBadRequest());
        call(post("/api/diary/entries"), token, """
                {"date": "%s", "mealType": "lunch", "foodId": 999999999, "grams": 10}
                """.formatted(DATE), status().isNotFound());
    }

    @Test
    void customGoalSurvivesProfileChangesUntilSwitchedBack() throws Exception {
        String token = register();
        String body = """
                {"sex": "female", "birthDate": "1995-03-10", "heightCm": 165, "weightKg": 70,
                 "activityLevel": "light", "goalType": "lose_fat", "targetWeightKg": 60}
                """;

        // Preview needs complete body data and never saves anything
        call(post("/api/me/goals/preview"), token, "{\"goalType\": \"bulk\"}", status().is(422));
        JsonNode preview = call(post("/api/me/goals/preview"), token, body, status().isOk());
        assertThat(preview.path("targets").path("kcal").asInt()).isBetween(1200, 2500);
        assertThat(preview.path("targetDate").asString()).isNotBlank();
        assertThat(preview.path("breakdown").path("tdee").asInt()).isPositive();
        call(get("/api/me/goals"), token, null, status().isNotFound());

        // Calculated mode: the goal follows the recommendation
        JsonNode me = call(put("/api/me"), token, body, status().isOk());
        assertThat(me.path("goalMode").asString()).isEqualTo("calculated");
        assertThat(me.path("targetWeightKg").decimalValue()).isEqualByComparingTo("60");
        assertThat(me.path("goal").path("source").asString()).isEqualTo("calculated");
        assertThat(me.path("goal").path("kcal").asInt()).isEqualTo(me.path("recommendation").path("targets").path("kcal").asInt());

        // Custom targets stick through later profile changes
        JsonNode custom = call(put("/api/me/goals"), token, """
                {"kcal": 1800, "proteinG": 150, "carbsG": 160, "fatG": 60}
                """, status().isOk());
        assertThat(custom.path("source").asString()).isEqualTo("custom");
        me = call(put("/api/me"), token, "{\"weightKg\": 68, \"goalType\": \"recomp\"}", status().isOk());
        assertThat(me.path("goalMode").asString()).isEqualTo("custom");
        assertThat(me.path("goal").path("kcal").asInt()).isEqualTo(1800);
        assertThat(me.path("recommendation").path("targets").path("kcal").asInt()).isNotEqualTo(1800);

        // Switching back recalculates right away
        me = call(put("/api/me"), token, "{\"goalMode\": \"calculated\"}", status().isOk());
        assertThat(me.path("goal").path("source").asString()).isEqualTo("calculated");
        assertThat(me.path("goal").path("kcal").asInt()).isEqualTo(me.path("recommendation").path("targets").path("kcal").asInt());

        call(put("/api/me"), token, "{\"weeklyRateKg\": 3}", status().isBadRequest());
        call(put("/api/me"), token, "{\"goalType\": \"shred\"}", status().isBadRequest());
    }

    @Test
    void refreshRotatesAndDetectsReuse() throws Exception {
        String email = uniqueEmail();
        JsonNode first = call(post("/api/auth/register"), null, credentials(email, "password123"),
                status().isCreated());
        String refresh1 = first.path("refreshToken").asString();

        JsonNode second = call(post("/api/auth/refresh"), null, refreshBody(refresh1), status().isOk());
        String refresh2 = second.path("refreshToken").asString();
        assertThat(refresh2).isNotEqualTo(refresh1);
        call(get("/api/me"), second.path("accessToken").asString(), null, status().isOk());

        // Reusing a rotated token revokes the whole family, including refresh2
        call(post("/api/auth/refresh"), null, refreshBody(refresh1), status().isUnauthorized());
        call(post("/api/auth/refresh"), null, refreshBody(refresh2), status().isUnauthorized());

        // Logging in again starts a fresh session; logout revokes it
        JsonNode third = call(post("/api/auth/login"), null, credentials(email, "password123"), status().isOk());
        String refresh3 = third.path("refreshToken").asString();
        call(post("/api/auth/logout"), null, refreshBody(refresh3), status().isNoContent());
        call(post("/api/auth/refresh"), null, refreshBody(refresh3), status().isUnauthorized());
    }

    private String register() throws Exception {
        JsonNode tokens = call(post("/api/auth/register"), null, credentials(uniqueEmail(), "password123"),
                status().isCreated());
        return tokens.path("accessToken").asString();
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

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password);
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\": \"%s\"}".formatted(refreshToken);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
