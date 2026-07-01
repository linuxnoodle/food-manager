package com.foodmanager.foodmanager.dto;

/**
 * One autocomplete suggestion for the ingredient picker. {@code tag} is the
 * canonical taxonomy ID ({@code "en:chicken"}) that should be sent back verbatim
 * as a value in {@code include=} or {@code exclude=}.
 */
public record IngredientSuggestion(
        String tag,
        String name
) {}
