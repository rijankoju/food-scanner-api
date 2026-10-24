package com.foodscan.diary;

import com.foodscan.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dates are the user's local calendar date, sent by the app, so "today"
 * follows the phone's time zone rather than the server's.
 */
@RestController
class DiaryController {

    record NewEntryRequest(
            @NotNull LocalDate date,
            @NotNull DiaryEntry.MealType mealType,
            @NotNull Long foodId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") BigDecimal grams,
            Long scanItemId) {
    }

    record UpdateEntryRequest(
            LocalDate date,
            DiaryEntry.MealType mealType,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") BigDecimal grams) {
    }

    private final DiaryService diaryService;

    DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @GetMapping("/api/diary")
    DiaryService.DayView day(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return diaryService.day(CurrentUser.id(jwt), date);
    }

    @PostMapping("/api/diary/entries")
    @ResponseStatus(HttpStatus.CREATED)
    DiaryService.EntryView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewEntryRequest request) {
        return diaryService.add(CurrentUser.id(jwt), new DiaryService.NewEntry(
                request.date(), request.mealType(), request.foodId(), request.grams(), request.scanItemId()));
    }

    @GetMapping("/api/diary/entries/{id}")
    DiaryService.EntryView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return diaryService.get(CurrentUser.id(jwt), id);
    }

    @GetMapping("/api/diary/recent-foods")
    List<DiaryService.RecentFood> recentFoods(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return diaryService.recentFoods(CurrentUser.id(jwt), limit);
    }

    @PutMapping("/api/diary/entries/{id}")
    DiaryService.EntryView update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                  @Valid @RequestBody UpdateEntryRequest request) {
        return diaryService.update(CurrentUser.id(jwt), id,
                new DiaryService.EntryUpdate(request.date(), request.mealType(), request.grams()));
    }

    @DeleteMapping("/api/diary/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        diaryService.delete(CurrentUser.id(jwt), id);
    }

    @GetMapping("/api/stats")
    List<DiaryEntryRepository.DailyTotal> stats(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return diaryService.dailyTotals(CurrentUser.id(jwt), from, to);
    }
}
