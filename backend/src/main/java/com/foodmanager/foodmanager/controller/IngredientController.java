package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.dto.IngredientSuggestion;
import com.foodmanager.foodmanager.service.IngredientSuggestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Ingredient autocomplete. Used by the search UI to populate the include/exclude
 * pickers — the returned {@code tag} strings are what /api/food/search expects.
 */
@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientSuggestService service;

    @GetMapping("/autocomplete")
    public List<IngredientSuggestion> autocomplete(@RequestParam("q") String q) {
        return service.suggest(q);
    }
}
