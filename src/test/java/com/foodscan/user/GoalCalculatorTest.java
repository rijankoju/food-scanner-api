package com.foodscan.user;

import static com.foodscan.user.GoalCalculator.Warning.AGGRESSIVE_PACE;
import static com.foodscan.user.GoalCalculator.Warning.FLOOR_APPLIED;
import static com.foodscan.user.GoalCalculator.Warning.LOW_TARGET_BMI;
import static com.foodscan.user.GoalCalculator.Warning.PACE_LIMITED;
import static com.foodscan.user.GoalCalculator.Warning.TARGET_DIRECTION;
import static org.assertj.core.api.Assertions.assertThat;

import com.foodscan.user.Profile.ActivityLevel;
import com.foodscan.user.Profile.GoalType;
import com.foodscan.user.Profile.Sex;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Expected values were computed independently with the same published formulas. */
class GoalCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);

    /** 31-year-old woman, 165 cm, 70 kg, lightly active: BMR 1415, daily burn 1946. */
    private static GoalCalculator.Recommendation woman(GoalType goal, Double target, Double rate) {
        return GoalCalculator.calculate(new GoalCalculator.BodyData(Sex.female, LocalDate.of(1995, 3, 10), 165, 70,
                ActivityLevel.light, goal, target, rate), TODAY);
    }

    /** 30-year-old man, 180 cm, 80 kg, moderately active: BMR 1780, daily burn 2759. */
    private static GoalCalculator.Recommendation man(GoalType goal, Double target, Double rate) {
        return GoalCalculator.calculate(new GoalCalculator.BodyData(Sex.male, LocalDate.of(1996, 1, 1), 180, 80,
                ActivityLevel.moderate, goal, target, rate), TODAY);
    }

    private static void assertTargets(GoalCalculator.Recommendation r, int kcal, int protein, int carbs, int fat) {
        assertThat(r.targets().kcal()).isEqualTo(kcal);
        assertThat(r.targets().proteinG()).isEqualByComparingTo(String.valueOf(protein));
        assertThat(r.targets().carbsG()).isEqualByComparingTo(String.valueOf(carbs));
        assertThat(r.targets().fatG()).isEqualByComparingTo(String.valueOf(fat));
    }

    @Test
    void maintainEatsAtDailyBurn() {
        GoalCalculator.Recommendation r = man(GoalType.maintain, null, null);

        assertTargets(r, 2760, 128, 390, 77);
        assertThat(r.breakdown().bmr()).isEqualTo(1780);
        assertThat(r.breakdown().tdee()).isEqualTo(2759);
        assertThat(r.breakdown().proteinPerKg()).isEqualTo(1.6);
        assertThat(r.breakdown().fatPercent()).isEqualTo(25);
        assertThat(r.warnings()).isEmpty();
        assertThat(r.targetDate()).isNull();
    }

    @Test
    void loseFatUsesPaceAndEstimatesTargetDate() {
        // 0.5 kg/week = 550 kcal/day deficit, within 25% of 2759
        GoalCalculator.Recommendation r = man(GoalType.lose_fat, 55.0, null);

        assertTargets(r, 2210, 176, 238, 61);
        assertThat(r.breakdown().adjustmentKcal()).isEqualTo(-549);
        assertThat(r.breakdown().weeklyRateKg()).isEqualTo(0.5);
        assertThat(r.weeksToTarget()).isEqualTo(50);
        assertThat(r.targetDate()).isEqualTo(LocalDate.of(2027, 5, 17));
        assertThat(r.warnings()).containsExactly(LOW_TARGET_BMI); // 55 kg at 180 cm is BMI 17
    }

    @Test
    void deficitIsCappedAtQuarterOfDailyBurn() {
        // 550 kcal would exceed 25% of 1946 (486), so pace drops to 0.44 kg/week
        GoalCalculator.Recommendation r = woman(GoalType.lose_fat, 60.0, null);

        assertTargets(r, 1460, 154, 117, 42);
        assertThat(r.breakdown().weeklyRateKg()).isEqualTo(0.44);
        assertThat(r.weeksToTarget()).isEqualTo(23);
        assertThat(r.targetDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(r.warnings()).containsExactly(PACE_LIMITED);
    }

    @Test
    void caloriesNeverGoBelowSafeMinimum() {
        // 60-year-old, 150 cm, 45 kg, sedentary: daily burn 1112
        GoalCalculator.Recommendation r = GoalCalculator.calculate(new GoalCalculator.BodyData(Sex.female,
                LocalDate.of(1966, 1, 1), 150, 45, ActivityLevel.sedentary, GoalType.lose_fat, null, 1.0), TODAY);

        assertTargets(r, 1200, 99, 126, 33);
        assertThat(r.warnings()).containsExactly(PACE_LIMITED, FLOOR_APPLIED);
    }

    @Test
    void gainGoalsAddSurplusAndRecompStaysAtMaintenance() {
        GoalCalculator.Recommendation lean = woman(GoalType.lean_gain, 75.0, null);
        assertTargets(lean, 2220, 140, 276, 62);
        assertThat(lean.breakdown().weeklyRateKg()).isEqualTo(0.25);
        assertThat(lean.weeksToTarget()).isEqualTo(20);

        assertTargets(woman(GoalType.bulk, null, null), 2390, 126, 322, 66);

        GoalCalculator.Recommendation recomp = woman(GoalType.recomp, null, null);
        assertTargets(recomp, 1950, 154, 212, 54);
        assertThat(recomp.breakdown().proteinPerKg()).isEqualTo(2.2);
    }

    @Test
    void paceIsClampedToTheGoalsRange() {
        // lean gain allows at most 0.25 kg/week
        assertThat(woman(GoalType.lean_gain, null, 0.9).breakdown().weeklyRateKg()).isEqualTo(0.25);
    }

    @Test
    void fastPaceForLightPersonIsFlagged() {
        // 0.5 kg/week is more than 1% of 45 kg
        GoalCalculator.Recommendation r = GoalCalculator.calculate(new GoalCalculator.BodyData(Sex.female,
                LocalDate.of(1995, 3, 10), 160, 45, ActivityLevel.moderate, GoalType.bulk, null, 0.5), TODAY);

        assertTargets(r, 2310, 81, 352, 64);
        assertThat(r.warnings()).containsExactly(AGGRESSIVE_PACE);
    }

    @Test
    void proteinUsesAdjustedWeightAboveBmi30() {
        // 120 kg at 170 cm is BMI 41.5; protein is based on 78 kg (BMI 27)
        GoalCalculator.Recommendation r = GoalCalculator.calculate(new GoalCalculator.BodyData(Sex.male,
                LocalDate.of(1986, 1, 1), 170, 120, ActivityLevel.sedentary, GoalType.maintain, null, null), TODAY);

        assertTargets(r, 2480, 125, 333, 72); // fat at its 0.6 g/kg floor
        assertThat(r.breakdown().proteinBasisKg()).isEqualTo(78.0);
    }

    @Test
    void targetInWrongDirectionGetsNoDate() {
        GoalCalculator.Recommendation r = man(GoalType.lose_fat, 90.0, null);

        assertThat(r.warnings()).containsExactly(TARGET_DIRECTION);
        assertThat(r.targetDate()).isNull();
        assertThat(r.weeksToTarget()).isNull();
    }
}
