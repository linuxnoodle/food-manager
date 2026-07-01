package com.foodmanager.foodmanager.dto.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Slim product shape returned by {@code /api/v2/search}. We request a pinned
 * {@code fields} set in the query (see OpenFoodFactsClient) so only these are present.
 * Anything else OFF returns is ignored via @JsonIgnoreProperties.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffV2Product(
        String code,
        @JsonProperty("product_name") String productName,
        String brands,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("image_front_small_url") String imageSmallUrl,
        @JsonProperty("ingredients_tags") List<String> ingredientsTags,
        @JsonProperty("allergens_tags") List<String> allergensTags,
        OffNutriments nutriments,
        @JsonProperty("nova_group") Integer novaGroup,
        @JsonProperty("nutriscore_grade") String nutriscoreGrade
) {}
