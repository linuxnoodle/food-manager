package com.foodmanager.foodmanager.service;

import com.foodmanager.foodmanager.dto.RecipeCreateRequest;
import com.foodmanager.foodmanager.dto.RecipeResponse;
import com.foodmanager.foodmanager.entity.Food;
import com.foodmanager.foodmanager.entity.Recipe;
import com.foodmanager.foodmanager.entity.RecipeIngredient;
import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.exception.FoodNotFoundException;
import com.foodmanager.foodmanager.exception.InvalidSearchQueryException;
import com.foodmanager.foodmanager.repo.FoodRepo;
import com.foodmanager.foodmanager.repo.RecipeRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Recipe create + list. Each ingredient has to resolve to a cached Food row, so
 * the per-100g nutrients can be added up across the recipe and split by
 * servings. We route every ingredient through FoodService first to make sure
 * it's cached (auto-fetch from OFF on a miss).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

    private static final Set<String> ALLOWED_UNITS = Set.of("g", "ml");

    private final RecipeRepo recipeRepo;
    private final FoodService foodService;
    private final FoodRepo foodRepo;

    @Transactional
    public RecipeResponse createRecipe(User author, RecipeCreateRequest req) {
        validate(req);

        Recipe recipe = new Recipe();
        recipe.setAuthor(author);
        recipe.setName(req.name().trim());
        recipe.setDescription(req.description());
        recipe.setServings(req.servings() <= 0 ? 1 : req.servings());
        recipe.setInstructions(req.instructions());

        // running totals across all ingredients, still on the per-100g basis
        double kcal = 0, protein = 0, fat = 0, carbs = 0, sugar = 0, salt = 0;
        boolean hasKcal = false, hasProtein = false, hasFat = false, hasCarbs = false, hasSugar = false, hasSalt = false;

        for (RecipeCreateRequest.IngredientInput in : req.ingredients()) {
            // resolve + cache via the detail path
            foodService.getByCode(in.code(), false);
            Food food = foodRepo.findById(in.code())
                    .orElseThrow(() -> new InvalidSearchQueryException("food not resolvable: " + in.code()));

            RecipeIngredient ri = new RecipeIngredient();
            ri.setRecipe(recipe);
            ri.setFood(food);
            ri.setQuantity(in.quantity());
            ri.setUnit(in.unit());
            recipe.getIngredients().add(ri);

            double factor = in.quantity() / 100.0;
            if (food.getKcal() != null) { kcal += food.getKcal() * factor; hasKcal = true; }
            if (food.getProteinG() != null) { protein += food.getProteinG() * factor; hasProtein = true; }
            if (food.getFatG() != null) { fat += food.getFatG() * factor; hasFat = true; }
            if (food.getCarbsG() != null) { carbs += food.getCarbsG() * factor; hasCarbs = true; }
            if (food.getSugarG() != null) { sugar += food.getSugarG() * factor; hasSugar = true; }
            if (food.getSaltG() != null) { salt += food.getSaltG() * factor; hasSalt = true; }
        }

        recipeRepo.save(recipe); // cascades to the ingredient rows
        log.info("user {} created recipe \"{}\" ({} ingredients)",
                author.getId(), recipe.getName(), recipe.getIngredients().size());

        return toResponse(recipe, new RecipeResponse.NutritionPerServing(
                hasKcal ? kcal / recipe.getServings() : null,
                hasProtein ? protein / recipe.getServings() : null,
                hasFat ? fat / recipe.getServings() : null,
                hasCarbs ? carbs / recipe.getServings() : null,
                hasSugar ? sugar / recipe.getServings() : null,
                hasSalt ? salt / recipe.getServings() : null
        ));
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> listRecipes(User author) {
        return recipeRepo.findByAuthorIdOrderByCreatedAtDesc(author.getId()).stream()
                .map(r -> toResponse(r, computeNutrition(r)))
                .toList();
    }

    @Transactional
    public void deleteRecipe(User user, UUID id) {
        Recipe recipe = recipeRepo.findById(id)
                .orElseThrow(() -> new FoodNotFoundException("recipe: " + id));
        if (!recipe.getAuthor().getId().equals(user.getId())) {
                throw new FoodNotFoundException("recipe: " + id);
        }
        recipeRepo.delete(recipe);
    }

    private void validate(RecipeCreateRequest req) {
        if (req == null) throw new InvalidSearchQueryException("body is required");
        if (req.name() == null || req.name().isBlank()) {
            throw new InvalidSearchQueryException("recipe name is required");
        }
        if (req.ingredients() == null || req.ingredients().isEmpty()) {
            throw new InvalidSearchQueryException("a recipe needs at least one ingredient");
        }
        for (RecipeCreateRequest.IngredientInput in : req.ingredients()) {
            if (in.code() == null || in.code().isBlank()) {
                throw new InvalidSearchQueryException("ingredient code is required");
            }
            if (in.quantity() <= 0) {
                throw new InvalidSearchQueryException("ingredient quantity must be > 0");
            }
            if (in.unit() == null || !ALLOWED_UNITS.contains(in.unit())) {
                throw new InvalidSearchQueryException("invalid ingredient unit (use one of " + ALLOWED_UNITS + ")");
            }
        }
    }

    // recompute nutrition for an already-stored recipe (read path)
    private RecipeResponse.NutritionPerServing computeNutrition(Recipe r) {
        double kcal = 0, protein = 0, fat = 0, carbs = 0, sugar = 0, salt = 0;
        boolean hasKcal = false, hasProtein = false, hasFat = false, hasCarbs = false, hasSugar = false, hasSalt = false;
        for (RecipeIngredient ri : r.getIngredients()) {
            Food f = ri.getFood();
            double factor = ri.getQuantity() / 100.0;
            if (f.getKcal() != null) { kcal += f.getKcal() * factor; hasKcal = true; }
            if (f.getProteinG() != null) { protein += f.getProteinG() * factor; hasProtein = true; }
            if (f.getFatG() != null) { fat += f.getFatG() * factor; hasFat = true; }
            if (f.getCarbsG() != null) { carbs += f.getCarbsG() * factor; hasCarbs = true; }
            if (f.getSugarG() != null) { sugar += f.getSugarG() * factor; hasSugar = true; }
            if (f.getSaltG() != null) { salt += f.getSaltG() * factor; hasSalt = true; }
        }
        int servings = r.getServings() <= 0 ? 1 : r.getServings();
        return new RecipeResponse.NutritionPerServing(
                hasKcal ? kcal / servings : null,
                hasProtein ? protein / servings : null,
                hasFat ? fat / servings : null,
                hasCarbs ? carbs / servings : null,
                hasSugar ? sugar / servings : null,
                hasSalt ? salt / servings : null
        );
    }

    private RecipeResponse toResponse(Recipe r, RecipeResponse.NutritionPerServing nutrition) {
        List<RecipeResponse.IngredientLine> lines = new ArrayList<>();
        for (RecipeIngredient ri : r.getIngredients()) {
            Food f = ri.getFood();
            lines.add(new RecipeResponse.IngredientLine(
                    f.getCode(), f.getName(), f.getImageUrl(), ri.getQuantity(), ri.getUnit()));
        }
        return new RecipeResponse(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getServings(),
                r.getInstructions(),
                lines,
                nutrition,
                r.getCreatedAt()
        );
    }
}
