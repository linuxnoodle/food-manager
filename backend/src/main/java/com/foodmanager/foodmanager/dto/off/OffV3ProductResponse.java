package com.foodmanager.foodmanager.dto.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope for {@code /api/v3/product/{code}.json}.
 * <p>
 * v3 differs from v2 in the success indicator:
 * <ul>
 *   <li>v3 {@code status} is a string ("success" / "failure"), not an integer.</li>
 *   <li>On failure, {@code errors} is non-empty and {@code product} is null.</li>
 *   <li>On success, {@code errors} is empty/absent and {@code product} is populated.</li>
 * </ul>
 * As of 2025-07, OFF v3 does NOT return ETag/Last-Modified headers, so the
 * conditional-GET path in the client effectively degrades to TTL-only — that's
 * fine, the code handles both cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OffV3ProductResponse(
        String status,
        OffV3Product product,
        @JsonProperty("errors") List<OffError> errors
) {
    public OffV3ProductResponse {
        if (errors == null) errors = List.of();
    }

    /** True when OFF says the call succeeded AND a product payload came back. */
    public boolean isFound() {
        return "success".equalsIgnoreCase(status) && product != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OffError(
            @JsonProperty("message") OffErrorMessage message
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record OffErrorMessage(String id) {}
    }
}

