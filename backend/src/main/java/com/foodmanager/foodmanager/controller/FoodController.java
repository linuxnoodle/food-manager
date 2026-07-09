package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.dto.FoodDetail;
import com.foodmanager.foodmanager.dto.FoodSearchPage;
import com.foodmanager.foodmanager.dto.MacroFilter;
import com.foodmanager.foodmanager.exception.InvalidSearchQueryException;
import com.foodmanager.foodmanager.service.FoodSearchService;
import com.foodmanager.foodmanager.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * All food-related endpoints. Routes under /api/food. Auth is required for all
 * of them — SecurityConfig's anyRequest().authenticated() covers this without
 * any extra rule.
 * <p>
 * Tag CSV format: {@code en:chicken,en:rice} — tags come from the autocomplete
 * endpoint, never user-typed free text (OFF's v2 search needs canonical IDs).
 */
@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;
    private final FoodSearchService foodSearchService;

    // digits only, length 1-24 (covers EAN-8/13/UPC with slack)
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{1,24}$");
    // canonical taxonomy ID, e.g. "en:chicken"
    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-z]{2}:[a-z0-9-]+$");

    /**
     * Structured ingredient search, proxied to OFF v2 (the only OFF endpoint with
     * filter-based search). Results are upserted into the local cache.
     */
    @GetMapping("/search")
    public FoodSearchPage search(
            @RequestParam(name = "include", required = false, defaultValue = "") String include,
            @RequestParam(name = "exclude", required = false, defaultValue = "") String exclude,
            @RequestParam(name = "minProtein", required = false) Double minProtein,
            @RequestParam(name = "maxCarbs", required = false) Double maxCarbs,
            @RequestParam(name = "maxSugar", required = false) Double maxSugar,
            @RequestParam(name = "maxFat", required = false) Double maxFat,
            @RequestParam(name = "maxSalt", required = false) Double maxSalt,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        List<String> includeTags = parseTags(include);
        List<String> excludeTags = parseTags(exclude);
        validateTags(includeTags);
        validateTags(excludeTags);
        MacroFilter macros = new MacroFilter(minProtein, maxCarbs, maxSugar, maxFat, maxSalt);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return foodSearchService.search(includeTags, excludeTags, macros, safePage, safeSize);
    }

    /**
     * Local-only search over previously-cached foods. Same query shape as /search
     * but never touches OFF — useful for offline mode or "things I've already looked at".
     */
    @GetMapping("/local-search")
    public FoodSearchPage localSearch(
            @RequestParam(name = "include", required = false, defaultValue = "") String include,
            @RequestParam(name = "exclude", required = false, defaultValue = "") String exclude,
            @RequestParam(name = "minProtein", required = false) Double minProtein,
            @RequestParam(name = "maxCarbs", required = false) Double maxCarbs,
            @RequestParam(name = "maxSugar", required = false) Double maxSugar,
            @RequestParam(name = "maxFat", required = false) Double maxFat,
            @RequestParam(name = "maxSalt", required = false) Double maxSalt,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        List<String> includeTags = parseTags(include);
        List<String> excludeTags = parseTags(exclude);
        validateTags(includeTags);
        validateTags(excludeTags);
        MacroFilter macros = new MacroFilter(minProtein, maxCarbs, maxSugar, maxFat, maxSalt);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return foodSearchService.searchLocal(includeTags, excludeTags, macros, safePage, safeSize);
    }

    /**
     * Full detail view of one food. Lookup is opaque — the {code} comes from a
     * prior search, not a user-typed barcode.
     */
    @GetMapping("/{code}")
    public FoodDetail getByCode(
            @PathVariable String code,
            @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new InvalidSearchQueryException("invalid code: " + code);
        }
        return foodService.getByCode(code, refresh);
    }

    // --- helpers ---

    private List<String> parseTags(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void validateTags(List<String> tags) {
        for (String t : tags) {
            if (!TAG_PATTERN.matcher(t).matches()) {
                throw new InvalidSearchQueryException("invalid ingredient tag (use /api/ingredients/autocomplete): " + t);
            }
        }
    }
}
