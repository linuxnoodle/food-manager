package com.foodmanager.foodmanager.service;

import com.foodmanager.foodmanager.client.OpenFoodFactsClient;
import com.foodmanager.foodmanager.client.OpenFoodFactsClient.ConditionalFetchResult;
import com.foodmanager.foodmanager.client.OpenFoodFactsClient.FetchResult;
import com.foodmanager.foodmanager.config.OpenFoodFactsProperties;
import com.foodmanager.foodmanager.dto.FoodDetail;
import com.foodmanager.foodmanager.dto.off.OffNutriments;
import com.foodmanager.foodmanager.dto.off.OffV3Product;
import com.foodmanager.foodmanager.entity.Food;
import com.foodmanager.foodmanager.exception.FoodNotFoundException;
import com.foodmanager.foodmanager.exception.UpstreamException;
import com.foodmanager.foodmanager.repo.FoodRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Tier 1 cache: one row per OFF product. Lookup flow:
 * <pre>
 * 1. findByCode → Optional<Food>
 * 2. if forceRefresh: skip to fetch
 * 3. cache hit + deleted  → 404 (tombstone)
 * 4. cache hit + age<TTL  → return cached
 * 5. cache hit + age>=TTL + no etag      → full GET (replace or tombstone)
 * 6. cache hit + age>=TTL + etag present → conditional GET
 * 7. cache miss                           → full GET
 * </pre>
 * On 5xx/timeout during refresh with a cached row available, we serve stale
 * rather than failing — nutrition data is rarely urgent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FoodService {

    private final FoodRepo foodRepo;
    private final OpenFoodFactsClient client;
    private final OpenFoodFactsProperties props;

    @Transactional
    public FoodDetail getByCode(String code, boolean forceRefresh) {
        Optional<Food> cached = foodRepo.findById(code);

        if (cached.isPresent() && !forceRefresh) {
            Food row = cached.get();
            if (row.isDeleted()) throw new FoodNotFoundException(code);
            if (isFresh(row)) return toDetail(row, true);
        }

        if (cached.isEmpty()) {
            return fetchMiss(code);
        }

        Food row = cached.get();
        if (row.isDeleted()) throw new FoodNotFoundException(code);

        if (row.getEtag() == null && row.getLastModified() == null) {
            return fetchFull(code, row);
        }
        return fetchConditional(code, row);
    }

    // --- cache-miss path (no row to fall back on; propagate OFF errors ---

    private FoodDetail fetchMiss(String code) {
        FetchResult result = client.fetchProduct(code);
        if (result instanceof FetchResult.Miss) throw new FoodNotFoundException(code);
        FetchResult.Hit hit = (FetchResult.Hit) result;
        Food food = toEntity(hit.product(), hit.etag(), hit.lastModified());
        foodRepo.save(food);
        return toDetail(food, false);
    }

    // --- full fetch against an existing row ---

    private FoodDetail fetchFull(String code, Food existing) {
        try {
            FetchResult result = client.fetchProduct(code);
            if (result instanceof FetchResult.Miss) {
                tombstone(existing);
                throw new FoodNotFoundException(code);
            }
            FetchResult.Hit hit = (FetchResult.Hit) result;
            updateEntity(existing, hit.product(), hit.etag(), hit.lastModified());
            foodRepo.save(existing);
            return toDetail(existing, false);
        } catch (UpstreamException e) {
            log.warn("OFF error fetching {}, serving stale: {}", code, e.getMessage());
            return toDetail(existing, true);
        }
    }

    // --- conditional fetch (If-None-Match / If-Modified-Since) ---

    private FoodDetail fetchConditional(String code, Food existing) {
        try {
            ConditionalFetchResult result = client.fetchProductConditional(
                    code, existing.getEtag(), existing.getLastModified());

            if (result instanceof ConditionalFetchResult.NotModified) {
                existing.setLastFetchedAt(Instant.now());
                foodRepo.save(existing);
                return toDetail(existing, true);
            }
            if (result instanceof ConditionalFetchResult.Miss) {
                tombstone(existing);
                throw new FoodNotFoundException(code);
            }
            ConditionalFetchResult.Modified modified = (ConditionalFetchResult.Modified) result;
            updateEntity(existing, modified.product(), modified.etag(), modified.lastModified());
            foodRepo.save(existing);
            return toDetail(existing, false);
        } catch (UpstreamException e) {
            log.warn("OFF error conditional-fetching {}, serving stale: {}", code, e.getMessage());
            return toDetail(existing, true);
        }
    }

    private void tombstone(Food existing) {
        existing.setDeleted(true);
        existing.setDeletedAt(Instant.now());
        foodRepo.save(existing);
    }

    private boolean isFresh(Food row) {
        return row.getLastFetchedAt().plus(props.cacheTtl()).isAfter(Instant.now());
    }

    // --- mapping (entity <-> dto, off dto -> entity) ---

    private Food toEntity(OffV3Product p, String etag, String lastModified) {
        Food f = new Food();
        f.setCode(p.code());
        applyProductFields(f, p);
        f.setEtag(etag);
        f.setLastModified(lastModified);
        f.setLastFetchedAt(Instant.now());
        f.setDeleted(false);
        return f;
    }

    private void updateEntity(Food f, OffV3Product p, String etag, String lastModified) {
        applyProductFields(f, p);
        // if the refresh didn't come back with new validator headers, keep the old ones
        // (some OFF responses drop ETag on certain conditions)
        if (etag != null) f.setEtag(etag);
        if (lastModified != null) f.setLastModified(lastModified);
        f.setLastFetchedAt(Instant.now());
        f.setDeleted(false);
        f.setDeletedAt(null);
    }

    private void applyProductFields(Food f, OffV3Product p) {
        f.setName(nullSafe(p.productName()));
        f.setBrand(p.brands());
        f.setQuantity(p.quantity());
        f.setImageUrl(p.imageUrl());
        f.setIngredientsText(p.ingredientsText());
        f.setIngredientsTags(nullToEmpty(p.ingredientsTags()));
        f.setAllergens(nullToEmpty(p.allergensTags()));
        f.setAdditives(nullToEmpty(p.additivesTags()));
        f.setNutriscoreGrade(p.nutriscoreGrade());
        f.setNovaGroup(p.novaGroup());
        OffNutriments n = p.nutriments();
        if (n != null) {
            f.setKcal(n.kcal());
            f.setProteinG(n.proteinG());
            f.setFatG(n.fatG());
            f.setCarbsG(n.carbsG());
            f.setSugarG(n.sugarG());
            f.setSaltG(n.saltG());
        }
    }

    private FoodDetail toDetail(Food f, boolean fromCache) {
        return new FoodDetail(
                f.getCode(), f.getName(), f.getBrand(), f.getQuantity(), f.getImageUrl(),
                f.getNutriscoreGrade(), f.getNovaGroup(),
                f.getIngredientsText(),
                f.getIngredientsTags(), f.getAllergens(), f.getAdditives(),
                f.getKcal(), f.getProteinG(), f.getFatG(), f.getCarbsG(), f.getSugarG(), f.getSaltG(),
                f.getLastFetchedAt(), fromCache
        );
    }

    private static Set<String> nullToEmpty(java.util.List<String> in) {
        return in == null ? new HashSet<>() : new HashSet<>(in);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
