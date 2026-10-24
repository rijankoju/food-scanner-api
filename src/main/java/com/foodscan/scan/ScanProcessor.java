package com.foodscan.scan;

import com.foodscan.food.Food;
import com.foodscan.food.FoodRepository;
import com.foodscan.storage.ImageKeys;
import com.foodscan.storage.ImageStorage;
import com.foodscan.storage.StorageProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs a scan in the background: read the photo, ask the vision model, match
 * each item to the food database, run checks, and save the result. The model
 * call happens outside any database transaction.
 */
@Component
class ScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScanProcessor.class);

    private final ScanRepository scans;
    private final FoodRepository foods;
    private final FoodMatcher matcher;
    private final FoodVisionClient vision;
    private final ImageStorage storage;
    private final StorageProperties storageProperties;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;

    ScanProcessor(ScanRepository scans, FoodRepository foods, FoodMatcher matcher, FoodVisionClient vision,
                  ImageStorage storage, StorageProperties storageProperties, TransactionTemplate tx,
                  ObjectMapper json, Clock clock) {
        this.scans = scans;
        this.foods = foods;
        this.matcher = matcher;
        this.vision = vision;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener
    void onScanRequested(ScanService.ScanRequested event) {
        process(event.scanId());
    }

    void process(long scanId) {
        Scan scan = scans.findById(scanId).orElseThrow();
        try {
            byte[] image;
            try {
                image = storage.read(scan.getImageKey(), storageProperties.maxImageBytes());
            } catch (Exception e) {
                log.info("Scan {}: could not read image {}: {}", scanId, scan.getImageKey(), e.toString());
                fail(scanId, "The photo wasn't found or is too large. Please upload it again.");
                return;
            }
            Optional<String> contentType = ImageKeys.sniffContentType(image);
            if (contentType.isEmpty()) {
                fail(scanId, "The upload isn't a JPEG, PNG or WebP image.");
                return;
            }

            long start = System.nanoTime();
            FoodVisionClient.Result result = vision.analyze(image, contentType.get());
            int latencyMs = (int) ((System.nanoTime() - start) / 1_000_000);
            String rawJson = json.writeValueAsString(result.analysis());

            tx.executeWithoutResult(_ -> saveResult(scanId, result, rawJson, latencyMs));
        } catch (FoodVisionClient.VisionException e) {
            fail(scanId, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Scan {} failed", scanId, e);
            fail(scanId, "Something went wrong analyzing this photo. Please try again.");
        }
    }

    private void saveResult(long scanId, FoodVisionClient.Result result, String rawJson, int latencyMs) {
        Scan scan = scans.findById(scanId).orElseThrow();
        List<MealAnalysis.Item> aiItems = result.analysis().items() == null ? List.of() : result.analysis().items();

        List<ScanItem> items = new ArrayList<>();
        for (MealAnalysis.Item raw : aiItems) {
            MealAnalysis.Item ai = ItemChecks.normalize(raw);
            ScanItem item = new ScanItem(scan, items.size(), ai);
            Optional<FoodMatcher.Match> match = matcher.match(ai);
            if (match.isPresent()) {
                Food food = foods.getReferenceById(match.get().foodId());
                item.match(food, ScanItem.Source.db, match.get().score());
                item.setFlags(ItemChecks.flags(ai, food, true));
            } else {
                item.match(aiEstimateFood(scan.getUserId(), ai), ScanItem.Source.ai, null);
                item.setFlags(ItemChecks.flags(ai, null, false));
            }
            items.add(item);
        }
        scan.complete(result, rawJson, latencyMs, items, clock.instant());
    }

    /**
     * Saves the model's own per-100 g estimate as a private food, so an
     * unmatched item can still be logged to the diary like any other food.
     */
    private Food aiEstimateFood(long userId, MealAnalysis.Item ai) {
        Food food = new Food("ai", null);
        food.setOwnerUserId(userId);
        food.setDataType("AI estimate");
        food.setName(ai.name());
        food.setKcal100g(decimal(ai.kcalPer100g()));
        food.setProtein100g(decimal(ai.proteinPer100g()));
        food.setCarbs100g(decimal(ai.carbsPer100g()));
        food.setFat100g(decimal(ai.fatPer100g()));
        food.setEnergyBasis("reported");
        return foods.save(food);
    }

    private void fail(long scanId, String message) {
        tx.executeWithoutResult(_ -> scans.findById(scanId).ifPresent(s -> s.fail(message, clock.instant())));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
