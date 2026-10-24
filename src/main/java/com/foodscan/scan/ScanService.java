package com.foodscan.scan;

import com.foodscan.food.Food;
import com.foodscan.food.FoodRepository;
import com.foodscan.food.Nutrients;
import com.foodscan.storage.ImageKeys;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScanService {

    /** Published when a scan is created; {@link ScanProcessor} picks it up after commit. */
    record ScanRequested(long scanId) {
    }

    /** Fields left null are not changed. */
    public record ItemUpdate(BigDecimal grams, Long foodId) {
    }

    private final ScanRepository scans;
    private final ScanCorrectionRepository corrections;
    private final FoodRepository foods;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int dailyLimit;

    ScanService(ScanRepository scans, ScanCorrectionRepository corrections, FoodRepository foods,
                ApplicationEventPublisher events, Clock clock,
                @Value("${foodscan.scan.daily-limit:50}") int dailyLimit) {
        this.scans = scans;
        this.corrections = corrections;
        this.foods = foods;
        this.events = events;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    @Transactional
    public ScanView create(long userId, String imageKey) {
        if (!ImageKeys.isOwnedBy(imageKey, ImageKeys.Kind.scans, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown upload key");
        }
        if (scans.countByUserIdAndCreatedAtAfter(userId, clock.instant().minus(Duration.ofHours(24))) >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily scan limit reached (" + dailyLimit + " per 24 hours)");
        }
        Scan scan = scans.save(new Scan(userId, imageKey));
        events.publishEvent(new ScanRequested(scan.getId()));
        return ScanView.from(scan, _ -> List.of());
    }

    @Transactional(readOnly = true)
    public ScanView get(long userId, long scanId) {
        Scan scan = find(userId, scanId);
        return ScanView.from(scan, item -> alternatives(item, userId));
    }

    /** Applies a user correction and records it for later evaluation. */
    @Transactional
    public ScanView.Item updateItem(long userId, long scanId, long itemId, ItemUpdate update) {
        ScanItem item = findItem(userId, scanId, itemId);
        if (update.grams() != null && update.grams().compareTo(item.getGrams()) != 0) {
            corrections.save(new ScanCorrection(item.getId(), "grams", item.getGrams(), update.grams()));
            item.setGrams(update.grams());
        }
        if (update.foodId() != null && !Objects.equals(update.foodId(), item.getMatchedFood().getId())) {
            Food food = foods.findById(update.foodId())
                    .filter(f -> f.getOwnerUserId() == null || f.getOwnerUserId() == userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
            corrections.save(new ScanCorrection(item.getId(), "food_id", item.getMatchedFood().getId(), food.getId()));
            item.match(food, ScanItem.Source.db, null);
            // The user chose this food, so database-match warnings no longer apply.
            item.setFlags(item.getFlags().stream()
                    .filter(f -> !f.equals(ItemChecks.NO_DB_MATCH) && !f.equals(ItemChecks.KCAL_MISMATCH)
                            && !f.equals(ItemChecks.MACRO_INCONSISTENT))
                    .toList());
        }
        return ScanView.item(item, alternatives(item, userId));
    }

    @Transactional
    public void removeItem(long userId, long scanId, long itemId) {
        ScanItem item = findItem(userId, scanId, itemId);
        if (!item.isRemoved()) {
            corrections.save(new ScanCorrection(item.getId(), "removed", false, true));
            item.remove();
        }
    }

    private List<ScanView.FoodOption> alternatives(ScanItem item, long userId) {
        Long matchedId = item.getMatchedFood().getId();
        return foods.search(item.getAiQuery(), 4).stream()
                .filter(r -> !r.getId().equals(matchedId))
                .limit(3)
                .map(r -> new ScanView.FoodOption(r.getId(), r.getName(), Nutrients.of(
                        r.getKcal100g(), r.getProtein100g(), r.getCarbs100g(), r.getFat100g())))
                .toList();
    }

    private Scan find(long userId, long scanId) {
        return scans.findWithItemsByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan not found"));
    }

    private ScanItem findItem(long userId, long scanId, long itemId) {
        Scan scan = find(userId, scanId);
        return scan.getItems().stream()
                .filter(i -> i.getId() == itemId && !i.isRemoved())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan item not found"));
    }
}
