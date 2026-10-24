package com.foodscan.scan;

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
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One detected food. {@code est*} and {@code ai*} fields keep what the model
 * said; {@code grams} and {@code matchedFood} are the current values, which
 * the user may correct.
 */
@Entity
@Table(name = "scan_items")
public class ScanItem {

    /** Where the nutrition values come from. */
    public enum Source { db, ai }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id")
    private Scan scan;

    private int position;

    @Column(name = "ai_name")
    private String aiName;

    @Column(name = "ai_query")
    private String aiQuery;

    @Column(name = "cooking_method")
    private String cookingMethod;

    @Column(name = "hidden_addition")
    private boolean hiddenAddition;

    @Column(name = "est_grams")
    private BigDecimal estGrams;

    @Column(name = "grams_low")
    private BigDecimal gramsLow;

    @Column(name = "grams_high")
    private BigDecimal gramsHigh;

    private BigDecimal confidence;

    private BigDecimal grams;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_food_id")
    private Food matchedFood;

    @Column(name = "match_score")
    private BigDecimal matchScore;

    @Enumerated(EnumType.STRING)
    private Source source;

    @Column(name = "ai_kcal_100g")
    private BigDecimal aiKcal100g;

    @Column(name = "ai_protein_100g")
    private BigDecimal aiProtein100g;

    @Column(name = "ai_carbs_100g")
    private BigDecimal aiCarbs100g;

    @Column(name = "ai_fat_100g")
    private BigDecimal aiFat100g;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> flags = new ArrayList<>();

    private boolean removed;

    protected ScanItem() {
    }

    ScanItem(Scan scan, int position, MealAnalysis.Item ai) {
        this.scan = scan;
        this.position = position;
        this.aiName = ai.name();
        this.aiQuery = ai.databaseQuery();
        this.cookingMethod = ai.cookingMethod();
        this.hiddenAddition = ai.hiddenAddition();
        this.estGrams = decimal(ai.estimatedGrams(), 1);
        this.gramsLow = decimal(ai.gramsLow(), 1);
        this.gramsHigh = decimal(ai.gramsHigh(), 1);
        this.confidence = decimal(ai.confidence(), 2);
        this.grams = estGrams;
        this.aiKcal100g = decimal(ai.kcalPer100g(), 2);
        this.aiProtein100g = decimal(ai.proteinPer100g(), 2);
        this.aiCarbs100g = decimal(ai.carbsPer100g(), 2);
        this.aiFat100g = decimal(ai.fatPer100g(), 2);
    }

    void match(Food food, Source source, Double score) {
        this.matchedFood = food;
        this.source = source;
        this.matchScore = score == null ? null : decimal(score, 4);
    }

    void setFlags(List<String> flags) {
        this.flags = new ArrayList<>(flags);
    }

    void setGrams(BigDecimal grams) {
        this.grams = grams;
    }

    void remove() {
        this.removed = true;
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public Scan getScan() { return scan; }
    public int getPosition() { return position; }
    public String getAiName() { return aiName; }
    public String getAiQuery() { return aiQuery; }
    public String getCookingMethod() { return cookingMethod; }
    public boolean isHiddenAddition() { return hiddenAddition; }
    public BigDecimal getEstGrams() { return estGrams; }
    public BigDecimal getGramsLow() { return gramsLow; }
    public BigDecimal getGramsHigh() { return gramsHigh; }
    public BigDecimal getConfidence() { return confidence; }
    public BigDecimal getGrams() { return grams; }
    public Food getMatchedFood() { return matchedFood; }
    public BigDecimal getMatchScore() { return matchScore; }
    public Source getSource() { return source; }
    public BigDecimal getAiKcal100g() { return aiKcal100g; }
    public List<String> getFlags() { return flags; }
    public boolean isRemoved() { return removed; }
}
