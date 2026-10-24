package com.foodscan.food;

import com.foodscan.common.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodRepository foodRepository;

    public FoodController(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @GetMapping("/search")
    public List<FoodSummary> search(
            @RequestParam @NotBlank @Size(max = 200) String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return foodRepository.search(q.strip(), limit).stream()
                .map(FoodSummary::from)
                .toList();
    }

    /** Public foods, plus the caller's own private ones (custom foods and AI estimates). */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public FoodDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long userId = CurrentUser.id(jwt);
        return foodRepository.findWithPortionsById(id)
                .filter(f -> f.getOwnerUserId() == null || f.getOwnerUserId() == userId)
                .map(FoodDetail::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Food not found"));
    }
}
