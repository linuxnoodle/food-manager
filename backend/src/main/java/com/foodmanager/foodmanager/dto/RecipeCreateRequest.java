package com.foodmanager.foodmanager.dto;

import java.util.List;

/**
 * Body for {@code POST /api/recipes}. Each ingredient {@code code} is an opaque
 * OFF product code (the one /api/food/search hands back). We resolve + cache it
 * through the food detail path so the nutrition can be added up.
 */
public record RecipeCreateRequest(
        String name,
        String description,
        int servings,
        String instructions,
        List<IngredientInput> ingredients
) {
    public record IngredientInput(String code, double quantity, String unit) {}
}
