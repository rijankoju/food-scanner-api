package com.foodscan.diary;

import com.foodscan.food.Food;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One logged food. Calories and macros are copied from the food when logged,
 * so later changes to the food never rewrite past days.
 */
@Entity
@Table(name = "diary_entries")
public class DiaryEntry {

    public enum MealType { breakfast, lunch, dinner, snack }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type")
    private MealType mealType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id")
    private Food food;

    private BigDecimal grams;
    private BigDecimal kcal;

    @Column(name = "protein_g")
    private BigDecimal proteinG;

    @Column(name = "carbs_g")
    private BigDecimal carbsG;

    @Column(name = "fat_g")
    private BigDecimal fatG;

    @Column(name = "scan_item_id")
    private Long scanItemId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected DiaryEntry() {
    }

    public DiaryEntry(Long userId, LocalDate date, MealType mealType, Food food, BigDecimal grams, Long scanItemId) {
        this.userId = userId;
        this.date = date;
        this.mealType = mealType;
        this.food = food;
        this.scanItemId = scanItemId;
        snapshotFromFood(grams);
    }

    /**
     * Computes nutrients as per-100 g value x grams / 100. If the food has no
     * energy value, it is derived from macros with Atwater factors (4/4/9).
     */
    private void snapshotFromFood(BigDecimal grams) {
        this.grams = grams;
        BigDecimal protein = orZero(food.getProtein100g());
        BigDecimal carbs = orZero(food.getCarbs100g());
        BigDecimal fat = orZero(food.getFat100g());
        BigDecimal kcal100g = food.getKcal100g() != null
                ? food.getKcal100g()
                : protein.multiply(BigDecimal.valueOf(4))
                        .add(carbs.multiply(BigDecimal.valueOf(4)))
                        .add(fat.multiply(BigDecimal.valueOf(9)));
        this.kcal = forGrams(kcal100g, grams);
        this.proteinG = forGrams(protein, grams);
        this.carbsG = forGrams(carbs, grams);
        this.fatG = forGrams(fat, grams);
    }

    /** Changes the amount, scaling the stored snapshot rather than re-reading the food. */
    public void changeGrams(BigDecimal newGrams) {
        BigDecimal factor = newGrams.divide(grams, 10, RoundingMode.HALF_UP);
        this.kcal = scale(kcal, factor);
        this.proteinG = scale(proteinG, factor);
        this.carbsG = scale(carbsG, factor);
        this.fatG = scale(fatG, factor);
        this.grams = newGrams;
    }

    public void moveTo(LocalDate date, MealType mealType) {
        this.date = date;
        this.mealType = mealType;
    }

    private static BigDecimal forGrams(BigDecimal per100g, BigDecimal grams) {
        return per100g.multiply(grams).divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal factor) {
        return value.multiply(factor).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public MealType getMealType() { return mealType; }
    public Food getFood() { return food; }
    public BigDecimal getGrams() { return grams; }
    public BigDecimal getKcal() { return kcal; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public Long getScanItemId() { return scanItemId; }
    public Instant getCreatedAt() { return createdAt; }
}
