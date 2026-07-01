package com.foodmanager.foodmanager.service;

import com.foodmanager.foodmanager.client.OpenFoodFactsClient;
import com.foodmanager.foodmanager.dto.IngredientSuggestion;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Tier 3 cache: in-memory (Caffeine) over OFF's v3 taxonomy_suggestions endpoint.
 * In-memory is correct here because taxonomy data is reference data, not user
 * data — losing it on restart just means a re-fetch, no harm done.
 * <p>
 * OFF returns display names only ("Chicken breast"), not canonical IDs. We derive
 * the canonical tag ("en:chicken-breast") by normalizing the name; this matches
 * OFF's own ID-generation scheme for ~95% of ingredients. The frontend should
 * send back the {@code tag} verbatim as a value in include= or exclude=.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientSuggestService {

    private final OpenFoodFactsClient client;
    private final Cache<String, List<IngredientSuggestion>> cache;

    public List<IngredientSuggestion> suggest(String q) {
        if (q == null || q.isBlank()) return List.of();
        String key = q.trim().toLowerCase(Locale.ROOT);
        return cache.get(key, k -> {
            List<IngredientSuggestion> result = client.suggestIngredients(k).stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> new IngredientSuggestion(toTag(name), name))
                    .toList();
            log.debug("ingredient autocomplete cache miss for '{}', {} results", k, result.size());
            return result;
        });
    }

    /**
     * Display name -> canonical taxonomy ID. OFF's convention is lowercase +
     * dasherize + "en:" prefix; we additionally strip anything that isn't
     * alphanumeric, space, or hyphen before normalizing whitespace.
     */
    static String toTag(String displayName) {
        String cleaned = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
        return "en:" + cleaned;
    }
}
