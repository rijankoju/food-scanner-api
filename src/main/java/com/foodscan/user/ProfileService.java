package com.foodscan.user;

import com.foodscan.storage.ImageKeys;
import com.foodscan.storage.ImageStorage;
import com.foodscan.storage.StorageProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    /** Fields left null are not changed. */
    public record ProfileUpdate(String displayName, Profile.Sex sex, LocalDate birthDate, BigDecimal heightCm,
                                BigDecimal weightKg, Profile.ActivityLevel activityLevel,
                                Profile.GoalType goalType, Profile.Units units, BigDecimal targetWeightKg,
                                BigDecimal weeklyRateKg, Profile.GoalMode goalMode) {
    }

    public record CurrentGoal(int kcal, BigDecimal proteinG, BigDecimal carbsG, BigDecimal fatG,
                              LocalDate effectiveFrom, Profile.GoalMode source) {

        static CurrentGoal from(Goal goal) {
            return new CurrentGoal(goal.getKcal(), goal.getProteinG(), goal.getCarbsG(), goal.getFatG(),
                    goal.getEffectiveFrom(), goal.getSource());
        }
    }

    /**
     * {@code goal} is what the diary uses; {@code recommendation} is what the
     * calculator suggests for the current profile (also in custom mode, so the
     * app can compare). It is null until the body data is complete.
     * {@code avatarUrl} is a path on this API (null without a photo); its
     * {@code v} parameter changes with every new photo, for cache busting.
     */
    public record Me(long id, String email, String displayName, String avatarUrl, Profile.Sex sex,
                     LocalDate birthDate,
                     BigDecimal heightCm, BigDecimal latestWeightKg, Profile.ActivityLevel activityLevel,
                     Profile.GoalType goalType, Profile.Units units, BigDecimal targetWeightKg,
                     BigDecimal weeklyRateKg, Profile.GoalMode goalMode, CurrentGoal goal,
                     GoalCalculator.Recommendation recommendation) {
    }

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final GoalRepository goals;
    private final WeightRepository weights;
    private final ImageStorage storage;
    private final StorageProperties storageProperties;
    private final Clock clock;

    public ProfileService(UserRepository users, ProfileRepository profiles, GoalRepository goals,
                          WeightRepository weights, ImageStorage storage, StorageProperties storageProperties,
                          Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.goals = goals;
        this.weights = weights;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Me get(long userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        Profile p = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        BigDecimal weight = latestWeight(userId);
        CurrentGoal goal = currentGoal(userId, today()).orElse(null);
        return new Me(user.getId(), user.getEmail(), p.getDisplayName(), avatarUrl(p), p.getSex(), p.getBirthDate(),
                p.getHeightCm(), weight, p.getActivityLevel(), p.getGoalType(), p.getUnits(),
                p.getTargetWeightKg(), p.getWeeklyRateKg(), p.getGoalMode(), goal,
                recommend(bodyData(p, weight)).orElse(null));
    }

    /**
     * Updates the profile and records today's weight if given. In calculated
     * mode, today's goal is recalculated once the body data is complete; in
     * custom mode the user's own targets are left alone.
     */
    @Transactional
    public Me update(long userId, ProfileUpdate update) {
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        if (update.displayName() != null) profile.setDisplayName(update.displayName());
        if (update.sex() != null) profile.setSex(update.sex());
        if (update.birthDate() != null) profile.setBirthDate(update.birthDate());
        if (update.heightCm() != null) profile.setHeightCm(update.heightCm());
        if (update.activityLevel() != null) profile.setActivityLevel(update.activityLevel());
        if (update.goalType() != null) profile.setGoalType(update.goalType());
        if (update.units() != null) profile.setUnits(update.units());
        if (update.targetWeightKg() != null) profile.setTargetWeightKg(update.targetWeightKg());
        if (update.weeklyRateKg() != null) profile.setWeeklyRateKg(update.weeklyRateKg());
        if (update.goalMode() != null) profile.setGoalMode(update.goalMode());
        profiles.save(profile);

        if (update.weightKg() != null) {
            recordWeight(userId, today(), update.weightKg());
        }

        if (profile.getGoalMode() == Profile.GoalMode.calculated) {
            recommend(bodyData(profile, latestWeight(userId)))
                    .ifPresent(r -> saveGoal(userId, r.targets(), Profile.GoalMode.calculated));
        }
        return get(userId);
    }

    /**
     * What the calculator would recommend with these (unsaved) changes applied
     * over the saved profile. Nothing is stored.
     */
    @Transactional(readOnly = true)
    public GoalCalculator.Recommendation preview(long userId, ProfileUpdate changes) {
        Profile saved = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        BigDecimal weight = changes.weightKg() != null ? changes.weightKg() : latestWeight(userId);
        Optional<GoalCalculator.BodyData> body = bodyData(
                pick(changes.sex(), saved.getSex()),
                pick(changes.birthDate(), saved.getBirthDate()),
                pick(changes.heightCm(), saved.getHeightCm()),
                weight,
                pick(changes.activityLevel(), saved.getActivityLevel()),
                pick(changes.goalType(), saved.getGoalType()),
                pick(changes.targetWeightKg(), saved.getTargetWeightKg()),
                pick(changes.weeklyRateKg(), saved.getWeeklyRateKg()));
        return recommend(body).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "Sex, birth date, height, weight, activity level and goal are needed for a recommendation"));
    }

    /** Sets custom targets effective today and switches the profile to custom mode. */
    @Transactional
    public CurrentGoal setCustomGoal(long userId, GoalCalculator.Targets targets) {
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        profile.setGoalMode(Profile.GoalMode.custom);
        profiles.save(profile);
        return CurrentGoal.from(saveGoal(userId, targets, Profile.GoalMode.custom));
    }

    /**
     * Uses an already uploaded image (from {@code POST /api/uploads/presign}
     * with kind avatars) as the profile photo.
     */
    @Transactional
    public Me setAvatar(long userId, String imageKey) {
        if (!ImageKeys.isOwnedBy(imageKey, ImageKeys.Kind.avatars, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown upload key");
        }
        if (readImage(imageKey).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Upload the photo first");
        }
        Profile profile = profiles.findById(userId).orElseGet(() -> new Profile(userId));
        profile.setAvatar(imageKey, clock.instant());
        profiles.save(profile);
        return get(userId);
    }

    @Transactional
    public Me removeAvatar(long userId) {
        profiles.findById(userId).ifPresent(profile -> profile.setAvatar(null, null));
        return get(userId);
    }

    public record Image(byte[] bytes, String contentType) {
    }

    /** The profile photo, if there is one. */
    @Transactional(readOnly = true)
    public Optional<Image> avatar(long userId) {
        return profiles.findById(userId)
                .map(Profile::getAvatarKey)
                .flatMap(this::readImage);
    }

    private Optional<Image> readImage(String key) {
        try {
            byte[] bytes = storage.read(key, storageProperties.maxImageBytes());
            return ImageKeys.sniffContentType(bytes).map(type -> new Image(bytes, type));
        } catch (IOException | RuntimeException e) {
            // Missing or unreadable object (S3 reports a missing key as an SdkException).
            return Optional.empty();
        }
    }

    private static String avatarUrl(Profile p) {
        return p.getAvatarKey() == null ? null
                : "/api/me/avatar?v=" + p.getAvatarUpdatedAt().toEpochMilli();
    }

    /**
     * The goal in force on {@code date}. Days before the first goal (e.g.
     * logged retroactively right after sign-up) use the earliest goal.
     */
    @Transactional(readOnly = true)
    public Optional<CurrentGoal> currentGoal(long userId, LocalDate date) {
        return goals.findFirstByUserIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(userId, date)
                .or(() -> goals.findFirstByUserIdOrderByEffectiveFromAsc(userId))
                .map(CurrentGoal::from);
    }

    @Transactional
    public Weight recordWeight(long userId, LocalDate date, BigDecimal kg) {
        Weight weight = weights.findById(new Weight.Key(userId, date)).orElseGet(() -> new Weight(userId, date, kg));
        weight.setKg(kg);
        return weights.save(weight);
    }

    private Optional<GoalCalculator.Recommendation> recommend(Optional<GoalCalculator.BodyData> body) {
        return body.map(b -> GoalCalculator.calculate(b, today()));
    }

    private static Optional<GoalCalculator.BodyData> bodyData(Profile p, BigDecimal weight) {
        return bodyData(p.getSex(), p.getBirthDate(), p.getHeightCm(), weight, p.getActivityLevel(),
                p.getGoalType(), p.getTargetWeightKg(), p.getWeeklyRateKg());
    }

    /** Empty unless everything the calculator needs is known. */
    private static Optional<GoalCalculator.BodyData> bodyData(Profile.Sex sex, LocalDate birthDate, BigDecimal heightCm,
                                                              BigDecimal weight, Profile.ActivityLevel activity,
                                                              Profile.GoalType goal, BigDecimal targetWeight,
                                                              BigDecimal weeklyRate) {
        if (sex == null || birthDate == null || heightCm == null || weight == null || activity == null || goal == null) {
            return Optional.empty();
        }
        return Optional.of(new GoalCalculator.BodyData(sex, birthDate, heightCm.doubleValue(), weight.doubleValue(),
                activity, goal, targetWeight == null ? null : targetWeight.doubleValue(),
                weeklyRate == null ? null : weeklyRate.doubleValue()));
    }

    private Goal saveGoal(long userId, GoalCalculator.Targets targets, Profile.GoalMode source) {
        LocalDate today = today();
        Goal goal = goals.findByUserIdAndEffectiveFrom(userId, today).orElseGet(() -> new Goal(userId, today));
        goal.setTargets(targets.kcal(), targets.proteinG(), targets.carbsG(), targets.fatG(), source);
        return goals.save(goal);
    }

    private BigDecimal latestWeight(long userId) {
        return weights.findFirstByUserIdOrderByDateDesc(userId).map(Weight::getKg).orElse(null);
    }

    private static <T> T pick(T change, T saved) {
        return change != null ? change : saved;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
