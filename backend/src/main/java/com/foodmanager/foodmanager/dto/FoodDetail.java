package com.foodmanager.foodmanager.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Full detail view returned by {@code GET /api/food/{code}}. Carries the full
 * ingredients text, the canonical ingredient tags, allergens, additives, and
 * per-100g nutrients, plus cache metadata so the UI can show "last updated X ago".
 */
public record FoodDetail(
        String code,
        String name,
        String brand,
        String quantity,
        String imageUrl,
        String nutriscoreGrade,
        Integer novaGroup,
        String ingredientsText,
        Set<String> ingredientsTags,
        Set<String> allergens,
        Set<String> additives,
        Double kcal,
        Double proteinG,
        Double fatG,
        Double carbsG,
        Double sugarG,
        Double saltG,
        Instant lastFetchedAt,
        boolean fromCache
) {}
