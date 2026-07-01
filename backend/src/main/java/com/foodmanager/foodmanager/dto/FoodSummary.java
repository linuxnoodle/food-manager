package com.foodmanager.foodmanager.dto;

import java.util.Set;

/**
 * Lightweight food item shown in search results. Allergens are included so the UI
 * can warn before the user clicks into a detail view.
 * <p>
 * {@code fromCache} is true when the response was served from our local H2 row
 * (Tier 1) or from a cached search payload (Tier 2); false when it was freshly
 * fetched from OFF during this request.
 */
public record FoodSummary(
        String code,
        String name,
        String brand,
        String imageUrl,
        String nutriscoreGrade,
        Integer novaGroup,
        Set<String> allergens,
        boolean fromCache
) {}
