package com.foodscan.user;

import com.foodscan.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
class MeController {

    record UpdateProfileRequest(
            @Size(max = 80) String displayName,
            Profile.Sex sex,
            @Past LocalDate birthDate,
            @DecimalMin("50") @DecimalMax("272") BigDecimal heightCm,
            @DecimalMin("20") @DecimalMax("400") BigDecimal weightKg,
            Profile.ActivityLevel activityLevel,
            Profile.GoalType goalType,
            Profile.Units units,
            @DecimalMin("20") @DecimalMax("400") BigDecimal targetWeightKg,
            @DecimalMin("0.05") @DecimalMax("1.0") BigDecimal weeklyRateKg,
            Profile.GoalMode goalMode) {

        ProfileService.ProfileUpdate toUpdate() {
            return new ProfileService.ProfileUpdate(displayName, sex, birthDate, heightCm, weightKg, activityLevel,
                    goalType, units, targetWeightKg, weeklyRateKg, goalMode);
        }
    }

    record CustomGoalRequest(
            @NotNull @Min(800) @Max(10000) Integer kcal,
            @NotNull @DecimalMin("0") @DecimalMax("600") BigDecimal proteinG,
            @NotNull @DecimalMin("0") @DecimalMax("1500") BigDecimal carbsG,
            @NotNull @DecimalMin("0") @DecimalMax("600") BigDecimal fatG) {
    }

    record AvatarRequest(@NotBlank String imageKey) {
    }

    private final ProfileService profileService;
    private final Clock clock;

    MeController(ProfileService profileService, Clock clock) {
        this.profileService = profileService;
        this.clock = clock;
    }

    @GetMapping
    ProfileService.Me get(@AuthenticationPrincipal Jwt jwt) {
        return profileService.get(CurrentUser.id(jwt));
    }

    @PutMapping
    ProfileService.Me update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(CurrentUser.id(jwt), request.toUpdate());
    }

    /** The recommendation for unsaved form values (merged over the saved profile). Saves nothing. */
    @PostMapping("/goals/preview")
    GoalCalculator.Recommendation preview(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.preview(CurrentUser.id(jwt), request.toUpdate());
    }

    @GetMapping("/goals")
    ProfileService.CurrentGoal goal(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        return profileService.currentGoal(CurrentUser.id(jwt), day)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No goal yet - complete your profile or set custom goals"));
    }

    @PutMapping("/goals")
    ProfileService.CurrentGoal setGoal(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody CustomGoalRequest request) {
        return profileService.setCustomGoal(CurrentUser.id(jwt), new GoalCalculator.Targets(
                request.kcal(), request.proteinG(), request.carbsG(), request.fatG()));
    }

    /** Sets the profile photo to an image uploaded with kind avatars. */
    @PutMapping("/avatar")
    ProfileService.Me setAvatar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AvatarRequest request) {
        return profileService.setAvatar(CurrentUser.id(jwt), request.imageKey());
    }

    @DeleteMapping("/avatar")
    ProfileService.Me removeAvatar(@AuthenticationPrincipal Jwt jwt) {
        return profileService.removeAvatar(CurrentUser.id(jwt));
    }

    /** The photo bytes. URLs carry a version parameter, so clients may cache them for long. */
    @GetMapping("/avatar")
    ResponseEntity<byte[]> avatar(@AuthenticationPrincipal Jwt jwt) {
        return profileService.avatar(CurrentUser.id(jwt))
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                        .body(image.bytes()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No profile photo"));
    }
}
