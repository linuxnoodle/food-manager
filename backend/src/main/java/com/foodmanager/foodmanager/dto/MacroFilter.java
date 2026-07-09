package com.foodmanager.foodmanager.dto;

/**
 * Optional per-100g macro bounds for /api/food/search. Each non-null field maps
 * 1:1 to an OFF v2 nutrient filter (minProtein -> proteins_100g>val, maxSugar ->
 * sugars_100g<val, ...). Note OFF silently ignores nutrient filters that aren't
 * anchored by a tag, so these only do anything alongside an include/exclude tag.
 */
public record MacroFilter(
        Double minProtein,
        Double maxCarbs,
        Double maxSugar,
        Double maxFat,
        Double maxSalt
) {
    public boolean isEmpty() {
        return minProtein == null && maxCarbs == null && maxSugar == null && maxFat == null && maxSalt == null;
    }
}
