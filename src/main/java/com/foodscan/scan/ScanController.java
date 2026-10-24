package com.foodscan.scan;

import com.foodscan.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scan flow for the app: presign and upload a photo (UploadController), then
 * POST here with the key and poll GET until status is {@code ready} or
 * {@code failed}.
 */
@RestController
@RequestMapping("/api/scans")
class ScanController {

    record CreateScanRequest(@NotBlank String imageKey) {
    }

    record UpdateItemRequest(@DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") BigDecimal grams,
                             Long foodId) {
    }

    private final ScanService scanService;

    ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ScanView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateScanRequest request) {
        return scanService.create(CurrentUser.id(jwt), request.imageKey());
    }

    @GetMapping("/{id}")
    ScanView get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return scanService.get(CurrentUser.id(jwt), id);
    }

    @PatchMapping("/{id}/items/{itemId}")
    ScanView.Item updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long itemId,
                             @Valid @RequestBody UpdateItemRequest request) {
        return scanService.updateItem(CurrentUser.id(jwt), id, itemId,
                new ScanService.ItemUpdate(request.grams(), request.foodId()));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long itemId) {
        scanService.removeItem(CurrentUser.id(jwt), id, itemId);
    }
}
