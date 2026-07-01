package com.foodmanager.foodmanager.dto;

import java.util.List;

/**
 * One page of search results. {@code totalCount} is the OFF-reported total
 * (matches-across-all-pages), not the size of {@code items}.
 */
public record FoodSearchPage(
        int page,
        int size,
        long totalCount,
        List<FoodSummary> items,
        boolean fromCache
) {}
