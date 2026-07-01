package com.foodmanager.foodmanager.dto.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The nutriments sub-object on an OFF product. Field names use OFF's exact
 * (sigh) mix of hyphens and underscores, mapped via @JsonProperty.
 * <p>
 * Per-100g values only. Per-serving values are ignored — they vary in serving_size
 * and aren't comparable across products.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffNutriments(
        @JsonProperty("energy-kcal_100g") Double kcal,
        @JsonProperty("proteins_100g") Double proteinG,
        @JsonProperty("fat_100g") Double fatG,
        @JsonProperty("carbohydrates_100g") Double carbsG,
        @JsonProperty("sugars_100g") Double sugarG,
        @JsonProperty("salt_100g") Double saltG
) {}
