package com.foodmanager.foodmanager.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A logged food event. Nutrients here are scaled by how much was actually eaten,
 * so the UI shows what you ate, not the 100g reference number.
 */
public record LogEntryResponse(
        UUID id,
        String code,
        String name,
        String imageUrl,
        double quantity,
        String unit,
        String meal,
        Instant loggedAt,
        Double kcal,
        Double proteinG,
        Double fatG,
        Double carbsG,
        Double sugarG,
        Double saltG
) {}
