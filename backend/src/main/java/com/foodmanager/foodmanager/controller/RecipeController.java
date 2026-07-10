package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.dto.RecipeCreateRequest;
import com.foodmanager.foodmanager.dto.RecipeResponse;
import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Recipe create + list. Same auth story as everything else —
 * anyRequest().authenticated() in SecurityConfig covers it, and the user comes
 * off the session cookie.
 */
@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping
    public ResponseEntity<RecipeResponse> createRecipe(
            @AuthenticationPrincipal User user,
            @RequestBody RecipeCreateRequest req) {
        RecipeResponse resp = recipeService.createRecipe(user, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public List<RecipeResponse> listRecipes(@AuthenticationPrincipal User user) {
        return recipeService.listRecipes(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipe(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        recipeService.deleteRecipe(user, id);
    }
}
