package com.foodscan.scan;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@link FoodVisionClient} backed by Claude vision with structured output. Active when foodscan.vision.provider=claude. */
@Component
@ConditionalOnProperty(name = "foodscan.vision.provider", havingValue = "claude")
class ClaudeFoodVisionClient implements FoodVisionClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeFoodVisionClient.class);

    private final VisionProperties.Claude properties;
    private volatile AnthropicClient client;

    ClaudeFoodVisionClient(VisionProperties properties) {
        this.properties = properties.claude();
    }

    @Override
    public Result analyze(byte[] image, String contentType) {
        StructuredMessageCreateParams.Builder<MealAnalysis> params = StructuredMessageCreateParams.<MealAnalysis>builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .system(VisionPrompt.SYSTEM)
                .outputConfig(StructuredOutputConfig.<MealAnalysis>builder()
                        .effort(OutputConfig.Effort.of(properties.effort()))
                        .format(MealAnalysis.class)
                        .build())
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofImage(Base64ImageSource.builder()
                                .mediaType(Base64ImageSource.MediaType.of(contentType))
                                .data(Base64.getEncoder().encodeToString(image))
                                .build()),
                        ContentBlockParam.ofText(VisionPrompt.USER)));
        if (properties.refusalFallback()) {
            params.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        StructuredMessage<MealAnalysis> response;
        try {
            response = client().messages().create(params.build());
        } catch (AnthropicException e) {
            log.warn("Vision request failed", e);
            throw new VisionException("The food analysis service is unavailable. Please try again.");
        }

        StopReason stopReason = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new VisionException("This photo couldn't be analyzed. Try another photo of the meal.");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new VisionException("The analysis was cut short. Try a photo with fewer items.");
        }
        MealAnalysis analysis = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new VisionException("The analysis came back empty. Please try again."));

        log.info("Vision analysis: model={} items={} inputTokens={} outputTokens={}",
                response.model().asString(), analysis.items() == null ? 0 : analysis.items().size(),
                response.usage().inputTokens(), response.usage().outputTokens());
        return new Result(analysis, response.model().asString(),
                response.usage().inputTokens(), response.usage().outputTokens());
    }

    /** Created on first use so the app starts without an API key (e.g. for tests or search-only use). */
    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    // The key may come from .env via Spring; otherwise use the SDK's own lookup.
                    client = properties.apiKey() == null || properties.apiKey().isBlank()
                            ? AnthropicOkHttpClient.fromEnv()
                            : AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build();
                }
            }
        }
        return client;
    }
}
