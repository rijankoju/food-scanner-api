package com.foodscan.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Body and preference data used to calculate calorie and macro goals. */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "height_cm")
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level")
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type")
    private GoalType goalType;

    @Enumerated(EnumType.STRING)
    private Units units = Units.metric;

    @Column(name = "target_weight_kg")
    private BigDecimal targetWeightKg;

    /** Planned change in kg per week (always positive; the goal type gives the direction). */
    @Column(name = "weekly_rate_kg")
    private BigDecimal weeklyRateKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_mode")
    private GoalMode goalMode = GoalMode.calculated;

    /** Storage key of the profile photo, or null. */
    @Column(name = "avatar_key")
    private String avatarKey;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    protected Profile() {
    }

    public Profile(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public Sex getSex() { return sex; }
    public LocalDate getBirthDate() { return birthDate; }
    public BigDecimal getHeightCm() { return heightCm; }
    public ActivityLevel getActivityLevel() { return activityLevel; }
    public GoalType getGoalType() { return goalType; }
    public Units getUnits() { return units; }
    public BigDecimal getTargetWeightKg() { return targetWeightKg; }
    public BigDecimal getWeeklyRateKg() { return weeklyRateKg; }
    public GoalMode getGoalMode() { return goalMode; }
    public String getAvatarKey() { return avatarKey; }
    public Instant getAvatarUpdatedAt() { return avatarUpdatedAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setSex(Sex sex) { this.sex = sex; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public void setActivityLevel(ActivityLevel activityLevel) { this.activityLevel = activityLevel; }
    public void setGoalType(GoalType goalType) { this.goalType = goalType; }
    public void setUnits(Units units) { this.units = units; }
    public void setTargetWeightKg(BigDecimal targetWeightKg) { this.targetWeightKg = targetWeightKg; }
    public void setWeeklyRateKg(BigDecimal weeklyRateKg) { this.weeklyRateKg = weeklyRateKg; }
    public void setGoalMode(GoalMode goalMode) { this.goalMode = goalMode; }

    /** Pass null to remove the photo. */
    public void setAvatar(String key, Instant updatedAt) {
        this.avatarKey = key;
        this.avatarUpdatedAt = updatedAt;
    }

    // Enum constants match the lowercase values allowed by the CHECK constraints.
    public enum Sex { male, female }

    public enum ActivityLevel {
        sedentary(1.2), light(1.375), moderate(1.55), active(1.725), very_active(1.9);

        private final double multiplier;

        ActivityLevel(double multiplier) {
            this.multiplier = multiplier;
        }

        public double multiplier() {
            return multiplier;
        }
    }

    public enum GoalType { lose_fat, maintain, lean_gain, bulk, recomp }

    /** calculated: targets follow the profile. custom: the user's own targets, never overwritten. */
    public enum GoalMode { calculated, custom }

    public enum Units { metric, imperial }
}
