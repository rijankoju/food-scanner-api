package com.foodscan.diary;

import com.foodscan.food.Food;
import com.foodscan.food.FoodRepository;
import com.foodscan.food.Nutrients;
import com.foodscan.user.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DiaryService {

    public record Macros(BigDecimal kcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG) {

        static final Macros ZERO = new Macros(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        Macros plus(DiaryEntry e) {
            return new Macros(kcal.add(e.getKcal()), proteinG.add(e.getProteinG()),
                    carbsG.add(e.getCarbsG()), fatG.add(e.getFatG()));
        }
    }

    public record EntryView(long id, LocalDate date, DiaryEntry.MealType mealType, long foodId, String foodName,
                            BigDecimal grams, BigDecimal kcal, BigDecimal proteinG, BigDecimal carbsG,
                            BigDecimal fatG, Long scanItemId) {

        static EntryView from(DiaryEntry e) {
            return new EntryView(e.getId(), e.getDate(), e.getMealType(), e.getFood().getId(),
                    e.getFood().getName(), e.getGrams(), e.getKcal(), e.getProteinG(), e.getCarbsG(),
                    e.getFatG(), e.getScanItemId());
        }
    }

    public record MealView(DiaryEntry.MealType mealType, Macros totals, List<EntryView> entries) {
    }

    /** {@code goal} and {@code remaining} are null until the user has a goal. */
    public record DayView(LocalDate date, ProfileService.CurrentGoal goal, Macros totals, Macros remaining,
                          List<MealView> meals) {
    }

    public record NewEntry(LocalDate date, DiaryEntry.MealType mealType, long foodId, BigDecimal grams,
                           Long scanItemId) {
    }

    /** Fields left null are not changed. */
    public record EntryUpdate(LocalDate date, DiaryEntry.MealType mealType, BigDecimal grams) {
    }

    private final DiaryEntryRepository entries;
    private final FoodRepository foods;
    private final ProfileService profileService;

    public DiaryService(DiaryEntryRepository entries, FoodRepository foods, ProfileService profileService) {
        this.entries = entries;
        this.foods = foods;
        this.profileService = profileService;
    }

    @Transactional(readOnly = true)
    public DayView day(long userId, LocalDate date) {
        List<DiaryEntry> dayEntries = entries.findByUserIdAndDateOrderByCreatedAt(userId, date);

        List<MealView> meals = Arrays.stream(DiaryEntry.MealType.values())
                .map(type -> {
                    List<DiaryEntry> mealEntries = dayEntries.stream().filter(e -> e.getMealType() == type).toList();
                    Macros totals = mealEntries.stream().reduce(Macros.ZERO, Macros::plus, (a, _) -> a);
                    return new MealView(type, totals, mealEntries.stream().map(EntryView::from).toList());
                })
                .toList();
        Macros totals = dayEntries.stream().reduce(Macros.ZERO, Macros::plus, (a, _) -> a);

        ProfileService.CurrentGoal goal = profileService.currentGoal(userId, date).orElse(null);
        Macros remaining = goal == null ? null : new Macros(
                BigDecimal.valueOf(goal.kcal()).subtract(totals.kcal()),
                goal.proteinG().subtract(totals.proteinG()),
                goal.carbsG().subtract(totals.carbsG()),
                goal.fatG().subtract(totals.fatG()));

        return new DayView(date, goal, totals, remaining, meals);
    }

    @Transactional
    public EntryView add(long userId, NewEntry request) {
        Food food = foods.findById(request.foodId())
                .filter(f -> f.getOwnerUserId() == null || f.getOwnerUserId() == userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
        DiaryEntry entry = entries.save(new DiaryEntry(userId, request.date(), request.mealType(), food,
                request.grams(), request.scanItemId()));
        return EntryView.from(entry);
    }

    public record RecentFood(long id, String name, Nutrients per100g, BigDecimal lastGrams) {
    }

    @Transactional(readOnly = true)
    public EntryView get(long userId, long entryId) {
        return EntryView.from(find(userId, entryId));
    }

    @Transactional(readOnly = true)
    public List<RecentFood> recentFoods(long userId, int limit) {
        return entries.recentFoods(userId, limit).stream()
                .map(r -> new RecentFood(r.getId(), r.getName(),
                        Nutrients.of(r.getKcal100g(), r.getProtein100g(), r.getCarbs100g(), r.getFat100g()),
                        r.getLastGrams()))
                .toList();
    }

    @Transactional
    public EntryView update(long userId, long entryId, EntryUpdate update) {
        DiaryEntry entry = find(userId, entryId);
        if (update.grams() != null) {
            entry.changeGrams(update.grams());
        }
        entry.moveTo(update.date() != null ? update.date() : entry.getDate(),
                update.mealType() != null ? update.mealType() : entry.getMealType());
        return EntryView.from(entry);
    }

    @Transactional
    public void delete(long userId, long entryId) {
        entries.delete(find(userId, entryId));
    }

    @Transactional(readOnly = true)
    public List<DiaryEntryRepository.DailyTotal> dailyTotals(long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Range must be 0-366 days with from <= to");
        }
        return entries.dailyTotals(userId, from, to);
    }

    private DiaryEntry find(long userId, long entryId) {
        return entries.findByIdAndUserId(entryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diary entry not found"));
    }
}
