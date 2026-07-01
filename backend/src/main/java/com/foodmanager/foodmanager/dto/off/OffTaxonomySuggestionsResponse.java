package com.foodmanager.foodmanager.dto.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope for {@code /api/v3/taxonomy_suggestions}. Note: {@code suggestions}
 * is a list of plain display-name strings (e.g. "Chicken breast"), NOT objects
 * with id/name. We derive canonical taxonomy IDs client-side by normalizing
 * the name (lowercase, dasherize, prefix with "en:") — this matches the way
 * OFF generates IDs in the vast majority of cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffTaxonomySuggestionsResponse(
        String status,
        List<String> suggestions
) {
    public OffTaxonomySuggestionsResponse {
        if (suggestions == null) suggestions = List.of();
    }
}
