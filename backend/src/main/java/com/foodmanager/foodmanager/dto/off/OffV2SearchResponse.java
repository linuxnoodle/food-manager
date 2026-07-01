package com.foodmanager.foodmanager.dto.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response from {@code /api/v2/search}. {@code count} is the total result count
 * (not the page size), {@code page} is 1-based.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffV2SearchResponse(
        Long count,
        Integer page,
        Integer pageSize,
        List<OffV2Product> products
) {
    public OffV2SearchResponse {
        if (products == null) products = List.of();
        if (count == null) count = 0L;
    }
}
