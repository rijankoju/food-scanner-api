package com.foodscan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily targets valid from {@code effectiveFrom} until the next goal starts,
 * so past diary days keep showing the goal that applied at the time.
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private int kcal;

    @Column(name = "protein_g")
    private BigDecimal proteinG;

    @Column(name = "carbs_g")
    private BigDecimal carbsG;

    @Column(name = "fat_g")
    private BigDecimal fatG;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    private Profile.GoalMode source = Profile.GoalMode.calculated;

    protected Goal() {
    }

    public Goal(Long userId, LocalDate effectiveFrom) {
        this.userId = userId;
        this.effectiveFrom = effectiveFrom;
    }

    public void setTargets(int kcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG,
                           Profile.GoalMode source) {
        this.source = source;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public int getKcal() { return kcal; }
    public BigDecimal getProteinG() { return proteinG; }
    public BigDecimal getCarbsG() { return carbsG; }
    public BigDecimal getFatG() { return fatG; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public Profile.GoalMode getSource() { return source; }
}
