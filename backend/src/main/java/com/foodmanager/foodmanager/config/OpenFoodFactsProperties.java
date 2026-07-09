package com.foodmanager.foodmanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OFF integration tunables. Bound from app.off.* in application.properties.
 * <p>
 * Note: app.off.user-agent is REQUIRED by OFF terms. Blank UA -> bot detection.
 */
@ConfigurationProperties(prefix = "app.off")
public record OpenFoodFactsProperties(
        String baseUrl,
        String userAgent,
        Duration cacheTtl,
        Duration searchCacheTtl,
        Duration taxonomyCacheTtl,
        Duration connectTimeout,
        Duration readTimeout,
        String mode,
        String duckdbPath,
        boolean selfhost,
        String dumpUrl,
        Duration resyncInterval
) {
    public OpenFoodFactsProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://world.openfoodfacts.org";
        if (userAgent == null || userAgent.isBlank()) userAgent = "FoodManager/0.1 (contact@example.com)";
        if (cacheTtl == null) cacheTtl = Duration.ofDays(30);
        if (searchCacheTtl == null) searchCacheTtl = Duration.ofHours(1);
        if (taxonomyCacheTtl == null) taxonomyCacheTtl = Duration.ofHours(24);
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(3);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(5);
        // mode: "remote" (call OFF over http, the default) or "local" (use the duckdb mirror)
        if (mode == null || mode.isBlank()) mode = "remote";
        // duckdb file for the OFF mirror. the ingest runner fills it in.
        if (duckdbPath == null || duckdbPath.isBlank()) duckdbPath = "./data/off/foodmanager.duckdb";
        // selfhost: launch flag -- app downloads + builds the mirror itself, no external scripts
        // dumpUrl: where the full JSONL dump is pulled from when selfhost=true
        if (dumpUrl == null || dumpUrl.isBlank()) {
            dumpUrl = "https://static.openfoodfacts.org/data/openfoodfacts-products.jsonl.gz";
        }
        // how often the selfhost flag re-pulls + reloads the dump
        if (resyncInterval == null) resyncInterval = Duration.ofHours(24);
    }
}
