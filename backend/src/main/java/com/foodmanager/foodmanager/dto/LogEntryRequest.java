package com.foodmanager.foodmanager.dto;

import java.time.Instant;

/**
 * Body for {@code POST /api/log}. {@code code} is the opaque OFF product code
 * you get back from /api/food/search. {@code loggedAt} is optional, falls back
 * to server-now if you leave it off.
 */
public record LogEntryRequest(
        String code,
        double quantity,
        String unit,
        String meal,
        Instant loggedAt
) {}
