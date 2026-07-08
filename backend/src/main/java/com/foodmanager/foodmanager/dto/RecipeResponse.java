package com.foodmanager.foodmanager.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recipe plus its ingredient lines and the worked-out per-serving nutrition
 * (each ingredient's scaled per-100g nutrients, added up, then divided by
 * servings). A nutrient is {@code null} when none of the ingredients had it.
 */
public record RecipeResponse(
        UUID id,
        String name,
        String description,
        int servings,
        String instructions,
        List<IngredientLine> ingredients,
        NutritionPerServing nutrition,
        Instant createdAt
) {
    public record IngredientLine(String code, String name, String imageUrl, double quantity, String unit) {}

    public record NutritionPerServing(
            Double kcal, Double proteinG, Double fatG,
            Double carbsG, Double sugarG, Double saltG
    ) {}
}
