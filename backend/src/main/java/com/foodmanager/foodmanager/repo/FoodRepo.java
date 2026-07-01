package com.foodmanager.foodmanager.repo;

import com.foodmanager.foodmanager.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface FoodRepo extends JpaRepository<Food, String> {
    // candidates for local-search: any food that has at least one of these ingredient tags.
    // we refine "has ALL of them" in-memory in FoodSearchService since the local cache is small.
    List<Food> findByIngredientsTagsInAndDeletedFalse(Collection<String> tags);

    List<Food> findByDeletedFalse();
}
