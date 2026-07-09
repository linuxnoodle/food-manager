package com.foodmanager.foodmanager.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.foodmanager.foodmanager.client.OpenFoodFactsClient;
import com.foodmanager.foodmanager.config.OpenFoodFactsProperties;
import com.foodmanager.foodmanager.dto.FoodSearchPage;
import com.foodmanager.foodmanager.dto.FoodSummary;
import com.foodmanager.foodmanager.dto.MacroFilter;
import com.foodmanager.foodmanager.dto.off.OffNutriments;
import com.foodmanager.foodmanager.dto.off.OffV2Product;
import com.foodmanager.foodmanager.dto.off.OffV2SearchResponse;
import com.foodmanager.foodmanager.entity.Food;
import com.foodmanager.foodmanager.entity.SearchQueryCache;
import com.foodmanager.foodmanager.exception.InvalidSearchQueryException;
import com.foodmanager.foodmanager.exception.UpstreamException;
import com.foodmanager.foodmanager.repo.FoodRepo;
import com.foodmanager.foodmanager.repo.SearchQueryCacheRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Search with two paths:
 * <ul>
 *   <li>{@link #search} — proxies to OFF v2 search (the only place that has the data),
 *       caches the result page under a deterministic key so repeat queries / pagination
 *       don't re-hit OFF. Each hit is upserted into {@code foods} as a side effect,
 *       populating the Tier 1 cache for free.</li>
 *   <li>{@link #searchLocal} — pure H2 query over previously-cached products.
 *       Used when offline or for "stuff I've looked up before" UX.</li>
 * </ul>
 * <p>
 * Concurrency: identical in-flight searches are coalesced via a per-key
 * {@link CompletableFuture} map so N simultaneous users typing the same filter
 * result in exactly one OFF call (the 10 req/min cap is the main reason this exists).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FoodSearchService {

    private final OpenFoodFactsClient client;
    private final FoodRepo foodRepo;
    private final SearchQueryCacheRepo searchCacheRepo;
    private final OpenFoodFactsProperties props;
    private final ObjectMapper objectMapper;

    /** key = queryKey, value = the in-flight fetch. Removed in whenComplete. */
    private final ConcurrentHashMap<String, CompletableFuture<FoodSearchPage>> inflight = new ConcurrentHashMap<>();

    // --- public API ---

    @Transactional
    public FoodSearchPage search(List<String> includeTags, List<String> excludeTags, MacroFilter macros, int page, int size) {
        if (includeTags.isEmpty() && excludeTags.isEmpty()) {
            throw new InvalidSearchQueryException("at least one of include/exclude must be non-empty");
        }
        String queryKey = buildQueryKey(includeTags, excludeTags, macros, page, size);

        // Tier 2: db cache
        SearchQueryCache cached = searchCacheRepo.findByQueryKey(queryKey).orElse(null);
        if (cached != null && isFresh(cached)) {
            return decode(cached.getPayloadJson(), true);
        }

        // Coalesce concurrent identical searches
        CompletableFuture<FoodSearchPage> future = inflight.computeIfAbsent(queryKey, k ->
                CompletableFuture.supplyAsync(() -> doSearch(includeTags, excludeTags, macros, page, size, queryKey))
                        .whenComplete((res, ex) -> inflight.remove(queryKey)));
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("interrupted waiting for search");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new UpstreamException("search failed: " + cause.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public FoodSearchPage searchLocal(List<String> includeTags, List<String> excludeTags, MacroFilter macros, int page, int size) {
        Set<String> include = new HashSet<>(includeTags);
        Set<String> exclude = new HashSet<>(excludeTags);

        // candidates: any food with at least one include tag (or all non-deleted if include is empty)
        List<Food> candidates = include.isEmpty()
                ? foodRepo.findByDeletedFalse()
                : foodRepo.findByIngredientsTagsInAndDeletedFalse(include);

        // refine: must contain ALL of include, NONE of exclude, and satisfy macro bounds
        List<Food> filtered = new ArrayList<>();
        for (Food f : candidates) {
            Set<String> tags = f.getIngredientsTags() == null ? Set.of() : f.getIngredientsTags();
            if (!tags.containsAll(include)) continue;
            if (!Collections.disjoint(exclude, tags)) continue;
            if (!matchesMacros(f, macros)) continue;
            filtered.add(f);
        }

        // stable ordering (so pagination is deterministic)
        filtered.sort((a, b) -> {
            int c = Objects.toString(a.getName(), "").compareToIgnoreCase(Objects.toString(b.getName(), ""));
            if (c != 0) return c;
            return Objects.toString(a.getCode(), "").compareTo(Objects.toString(b.getCode(), ""));
        });

        int total = filtered.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<FoodSummary> items = new ArrayList<>();
        for (Food f : filtered.subList(from, to)) {
            items.add(toSummary(f, true));
        }
        return new FoodSearchPage(page, size, total, items, true);
    }

    // --- internals ---

    private FoodSearchPage doSearch(List<String> includeTags, List<String> excludeTags, MacroFilter macros, int page, int size, String queryKey) {
        OffV2SearchResponse resp = client.search(includeTags, excludeTags, macros, page, size);

        List<FoodSummary> items = new ArrayList<>(resp.products().size());
        for (OffV2Product p : resp.products()) {
            if (p.code() == null || p.code().isBlank()) continue;
            Food upserted = upsert(p);
            items.add(toSummary(upserted, false));
        }

        FoodSearchPage result = new FoodSearchPage(page, size, resp.count(), items, false);

        // persist Tier 2 cache entry (insert or update)
        SearchQueryCache existing = searchCacheRepo.findByQueryKey(queryKey).orElse(null);
        String payload = encode(result);
        if (existing == null) {
            searchCacheRepo.save(new SearchQueryCache(queryKey, Instant.now(), payload));
        } else {
            existing.setPayloadJson(payload);
            existing.setLastFetchedAt(Instant.now());
            searchCacheRepo.save(existing);
        }
        return result;
    }

    /** Side-effect: every search hit is upserted into foods so detail lookups are cache hits. */
    private Food upsert(OffV2Product p) {
        Food f = foodRepo.findById(p.code()).orElseGet(Food::new);
        f.setCode(p.code());
        f.setName(nullSafe(p.productName()));
        f.setBrand(p.brands());
        f.setImageUrl(p.imageUrl() != null ? p.imageUrl() : p.imageSmallUrl());
        f.setIngredientsTags(nullToEmpty(p.ingredientsTags()));
        f.setAllergens(nullToEmpty(p.allergensTags()));
        f.setNutriscoreGrade(p.nutriscoreGrade());
        f.setNovaGroup(p.novaGroup());
        OffNutriments n = p.nutriments();
        if (n != null) {
            // only fill in nutrients that aren't already set from a richer v3 fetch
            if (f.getKcal() == null && n.kcal() != null) f.setKcal(n.kcal());
            if (f.getProteinG() == null && n.proteinG() != null) f.setProteinG(n.proteinG());
            if (f.getFatG() == null && n.fatG() != null) f.setFatG(n.fatG());
            if (f.getCarbsG() == null && n.carbsG() != null) f.setCarbsG(n.carbsG());
            if (f.getSugarG() == null && n.sugarG() != null) f.setSugarG(n.sugarG());
            if (f.getSaltG() == null && n.saltG() != null) f.setSaltG(n.saltG());
        }
        // v2 search doesn't give us ETag/Last-Modified, so leave cache headers untouched.
        // lastFetchedAt only advances when a v3 detail fetch actually validates the row,
        // otherwise stale food data from a brief search glance would set the TTL forever.
        if (f.getLastFetchedAt() == null) {
            f.setLastFetchedAt(Instant.now());
        }
        f.setDeleted(false);
        return foodRepo.save(f);
    }

    private boolean isFresh(SearchQueryCache row) {
        return row.getLastFetchedAt().plus(props.searchCacheTtl()).isAfter(Instant.now());
    }

    /**
     * Deterministic key: include-sorted | exclude-sorted | macros | page | size. Sorted so
     * identical sets in different order hit the same cache row.
     */
    private String buildQueryKey(List<String> includeTags, List<String> excludeTags, MacroFilter macros, int page, int size) {
        String inc = new TreeSet<>(includeTags).toString();
        String exc = new TreeSet<>(excludeTags).toString();
        String mc = macros == null ? "" : macros.toString();
        String raw = "i=" + inc + "|e=" + exc + "|m=" + mc + "|p=" + page + "|s=" + size;
        return sha256(raw);
    }

    // a food matches if every bound is satisfied; a missing nutrient value can't be
    // verified, so we drop it (consistent with "filter by macro")
    private boolean matchesMacros(Food f, MacroFilter m) {
        if (m == null || m.isEmpty()) return true;
        if (m.minProtein() != null && (f.getProteinG() == null || f.getProteinG() < m.minProtein())) return false;
        if (m.maxCarbs() != null && (f.getCarbsG() == null || f.getCarbsG() > m.maxCarbs())) return false;
        if (m.maxSugar() != null && (f.getSugarG() == null || f.getSugarG() > m.maxSugar())) return false;
        if (m.maxFat() != null && (f.getFatG() == null || f.getFatG() > m.maxFat())) return false;
        if (m.maxSalt() != null && (f.getSaltG() == null || f.getSaltG() > m.maxSalt())) return false;
        return true;
    }

    private static String sha256(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new UpstreamException("SHA-256 not available", e);
        }
    }

    private String encode(FoodSearchPage page) {
        try {
            return objectMapper.writeValueAsString(page);
        } catch (JacksonException e) {
            throw new UpstreamException("could not serialize search cache payload", e);
        }
    }

    private FoodSearchPage decode(String json, boolean fromCache) {
        try {
            FoodSearchPage p = objectMapper.readValue(json, FoodSearchPage.class);
            // override fromCache from serialization — a Tier-2 hit is by definition cached
            return new FoodSearchPage(p.page(), p.size(), p.totalCount(), p.items(), fromCache);
        } catch (JacksonException e) {
            throw new UpstreamException("could not deserialize search cache payload", e);
        }
    }

    private FoodSummary toSummary(Food f, boolean fromCache) {
        return new FoodSummary(
                f.getCode(), f.getName(), f.getBrand(), f.getImageUrl(),
                f.getNutriscoreGrade(), f.getNovaGroup(),
                f.getAllergens() == null ? Set.of() : f.getAllergens(),
                fromCache
        );
    }

    private static Set<String> nullToEmpty(List<String> in) {
        return in == null ? new HashSet<>() : new HashSet<>(in);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
