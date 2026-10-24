package com.foodscan.user;

import com.foodscan.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weights")
class WeightController {

    record WeightRequest(@NotNull LocalDate date, @NotNull @DecimalMin("20") @DecimalMax("400") BigDecimal kg) {
    }

    record WeightResponse(LocalDate date, BigDecimal kg) {
    }

    private final ProfileService profileService;
    private final WeightRepository weights;

    WeightController(ProfileService profileService, WeightRepository weights) {
        this.profileService = profileService;
        this.weights = weights;
    }

    /** Records the weight for a day, replacing any earlier value for that day. */
    @PostMapping
    WeightResponse record(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WeightRequest request) {
        Weight weight = profileService.recordWeight(CurrentUser.id(jwt), request.date(), request.kg());
        return new WeightResponse(weight.getDate(), weight.getKg());
    }

    @GetMapping
    List<WeightResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return weights.findByUserIdAndDateBetweenOrderByDate(CurrentUser.id(jwt), from, to).stream()
                .map(w -> new WeightResponse(w.getDate(), w.getKg()))
                .toList();
    }
}
