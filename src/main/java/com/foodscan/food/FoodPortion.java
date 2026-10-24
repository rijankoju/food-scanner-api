package com.foodscan.food;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A household measure for a food, e.g. "1 cup" = 244 g. */
@Entity
@Table(name = "food_portions")
public class FoodPortion {

    /** A portion before it is attached to a food. */
    public record Measure(String label, BigDecimal grams) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "food_portions_seq")
    @SequenceGenerator(name = "food_portions_seq", sequenceName = "food_portions_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id")
    private Food food;

    private String label;
    private BigDecimal grams;
    private int position;

    protected FoodPortion() {
    }

    FoodPortion(Food food, String label, BigDecimal grams, int position) {
        this.food = food;
        this.label = label;
        this.grams = grams;
        this.position = position;
    }

    public Long getId() { return id; }
    public Food getFood() { return food; }
    public String getLabel() { return label; }
    public BigDecimal getGrams() { return grams; }
    public int getPosition() { return position; }
}
