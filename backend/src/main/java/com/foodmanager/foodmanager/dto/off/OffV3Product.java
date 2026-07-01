package com.foodmanager.foodmanager.dto.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Full product shape from {@code /api/v3/product/{code}.json}. We request a pinned
 * {@code fields} set in the query. {@code last_modified_t} is epoch-seconds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffV3Product(
        String code,
        @JsonProperty("product_name") String productName,
        String brands,
        String quantity,
        @JsonProperty("image_url") String imageUrl,
        @JsonProperty("ingredients_text") String ingredientsText,
        @JsonProperty("ingredients_tags") List<String> ingredientsTags,
        @JsonProperty("allergens_tags") List<String> allergensTags,
        @JsonProperty("additives_tags") List<String> additivesTags,
        @JsonProperty("nova_group") Integer novaGroup,
        @JsonProperty("nutriscore_grade") String nutriscoreGrade,
        OffNutriments nutriments,
        @JsonProperty("last_modified_t") Long lastModifiedT
) {}
