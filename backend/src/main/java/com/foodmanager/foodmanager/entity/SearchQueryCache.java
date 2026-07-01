package com.foodmanager.foodmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tier 2 cache: maps a deterministic search-query key to its last OFF result page,
 * so repeat searches (or pagination back/forth) don't re-hit OFF.
 * <p>
 * The key is sha256(include | exclude | page | size) — see FoodSearchService.
 * Payload is the serialized FoodSearchPage JSON.
 */
@Entity
@Table(name = "search_cache",
        indexes = @Index(name = "idx_sc_last_fetched", columnList = "last_fetched_at"))
@NoArgsConstructor
@Getter
@Setter
public class SearchQueryCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_key", nullable = false, unique = true, length = 512)
    private String queryKey;

    @Column(name = "last_fetched_at", nullable = false)
    private Instant lastFetchedAt;

    @Column(name = "payload_json", nullable = false, length = 16000)
    private String payloadJson;

    public SearchQueryCache(String queryKey, Instant lastFetchedAt, String payloadJson) {
        this.queryKey = queryKey;
        this.lastFetchedAt = lastFetchedAt;
        this.payloadJson = payloadJson;
    }
}
