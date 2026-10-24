package com.foodscan.user;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Recommended daily calories and macros from body data and a goal.
 *
 * <ul>
 *   <li>BMR: Mifflin-St Jeor. Daily burn (TDEE): BMR x activity multiplier.</li>
 *   <li>Lose fat / lean gain / bulk: TDEE -/+ pace x 7700 kcal per kg / 7 days.
 *       Deficits are capped at 25% of TDEE; calories never go below 1200 (female)
 *       or 1500 (male). Maintain and recomp eat at TDEE.</li>
 *   <li>Protein per kg by goal, using the weight at BMI 27 when BMI is over 30.
 *       Fat is 25% of calories but at least 0.6 g/kg. Carbs are the remainder.</li>
 * </ul>
 * Every rule that changes the result adds a {@link Warning} so the app can explain it.
 */
public final class GoalCalculator {

    /** Energy in one kg of body-weight change, the usual rule of thumb. */
    static final double KCAL_PER_KG = 7700;

    public enum Warning {
        /** The chosen pace needed a deficit over 25% of daily burn, so it was slowed. */
        PACE_LIMITED,
        /** Calories were raised to the safe minimum. */
        FLOOR_APPLIED,
        /** Pace is over 1% of body weight per week. */
        AGGRESSIVE_PACE,
        /** Protein and fat leave no room for carbs. */
        LOW_CARBS,
        /** The target weight is in the opposite direction from the goal. */
        TARGET_DIRECTION,
        /** The target weight is below a BMI of 18.5. */
        LOW_TARGET_BMI
    }

    public record Targets(int kcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG) {
    }

    /** {@code targetWeightKg} and {@code weeklyRateKg} are optional. */
    public record BodyData(Profile.Sex sex, LocalDate birthDate, double heightCm, double weightKg,
                           Profile.ActivityLevel activityLevel, Profile.GoalType goalType,
                           Double targetWeightKg, Double weeklyRateKg) {
    }

    /** How the targets were derived, for the app's "why these numbers" view. */
    public record Breakdown(int bmr, int tdee, int adjustmentKcal, double weeklyRateKg, double proteinPerKg,
                            double proteinBasisKg, int fatPercent) {
    }

    /** {@code targetDate} and {@code weeksToTarget} are null without a reachable target weight. */
    public record Recommendation(Targets targets, Breakdown breakdown, Integer weeksToTarget, LocalDate targetDate,
                                 List<Warning> warnings) {
    }

    /** Pace in kg/week: used when none is chosen, and the allowed range for the goal. */
    public record Pace(double defaultRate, double min, double max) {
    }

    private GoalCalculator() {
    }

    /** The pace range for a goal, or null if the goal doesn't change weight. */
    public static Pace pace(Profile.GoalType goal) {
        return switch (goal) {
            case lose_fat -> new Pace(0.5, 0.25, 1.0);
            case lean_gain -> new Pace(0.25, 0.1, 0.25);
            case bulk -> new Pace(0.4, 0.25, 0.5);
            case maintain, recomp -> null;
        };
    }

    public static Recommendation calculate(BodyData body, LocalDate today) {
        List<Warning> warnings = new ArrayList<>();
        Profile.GoalType goal = body.goalType();
        double weight = body.weightKg();
        double heightM = body.heightCm() / 100;

        int age = Period.between(body.birthDate(), today).getYears();
        double bmr = 10 * weight + 6.25 * body.heightCm() - 5 * age + (body.sex() == Profile.Sex.male ? 5 : -161);
        double tdee = bmr * body.activityLevel().multiplier();

        Pace pace = pace(goal);
        double rate = 0;
        double adjustment = 0;
        if (pace != null) {
            rate = body.weeklyRateKg() == null
                    ? pace.defaultRate()
                    : Math.max(pace.min(), Math.min(pace.max(), body.weeklyRateKg()));
            double dailyKcal = rate * KCAL_PER_KG / 7;
            if (goal == Profile.GoalType.lose_fat) {
                double maxDeficit = 0.25 * tdee;
                if (dailyKcal > maxDeficit) {
                    dailyKcal = maxDeficit;
                    rate = dailyKcal * 7 / KCAL_PER_KG;
                    warnings.add(Warning.PACE_LIMITED);
                }
                adjustment = -dailyKcal;
            } else {
                adjustment = dailyKcal;
            }
            if (rate > 0.01 * weight) {
                warnings.add(Warning.AGGRESSIVE_PACE);
            }
        }

        double kcal = tdee + adjustment;
        double floor = body.sex() == Profile.Sex.male ? 1500 : 1200;
        if (kcal < floor) {
            kcal = floor;
            warnings.add(Warning.FLOOR_APPLIED);
        }
        kcal = Math.round(kcal / 10.0) * 10.0;

        double bmi = weight / (heightM * heightM);
        double proteinBasis = bmi > 30 ? 27 * heightM * heightM : weight;
        double proteinPerKg = switch (goal) {
            case lose_fat, recomp -> 2.2;
            case lean_gain -> 2.0;
            case bulk -> 1.8;
            case maintain -> 1.6;
        };
        double protein = proteinBasis * proteinPerKg;
        double fat = Math.max(kcal * 0.25 / 9, 0.6 * weight);
        double carbs = (kcal - protein * 4 - fat * 9) / 4;
        if (carbs < 0) {
            carbs = 0;
            warnings.add(Warning.LOW_CARBS);
        }

        Integer weeks = null;
        LocalDate targetDate = null;
        if (body.targetWeightKg() != null) {
            double target = body.targetWeightKg();
            double change = target - weight;
            boolean wantsLoss = goal == Profile.GoalType.lose_fat;
            boolean wantsGain = goal == Profile.GoalType.lean_gain || goal == Profile.GoalType.bulk;
            if ((wantsLoss && change > 0) || (wantsGain && change < 0)) {
                warnings.add(Warning.TARGET_DIRECTION);
            } else if ((wantsLoss || wantsGain) && rate > 0 && Math.abs(change) > 0) {
                weeks = (int) Math.ceil(Math.abs(change) / rate);
                targetDate = today.plusWeeks(weeks);
            }
            if (target / (heightM * heightM) < 18.5) {
                warnings.add(Warning.LOW_TARGET_BMI);
            }
        }

        Targets targets = new Targets((int) kcal, grams(protein), grams(carbs), grams(fat));
        Breakdown breakdown = new Breakdown(
                (int) Math.round(bmr), (int) Math.round(tdee), (int) Math.round(kcal - tdee),
                round2(rate), proteinPerKg, round1(proteinBasis), (int) Math.round(fat * 9 / kcal * 100));
        return new Recommendation(targets, breakdown, weeks, targetDate, List.copyOf(warnings));
    }

    private static BigDecimal grams(double value) {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
