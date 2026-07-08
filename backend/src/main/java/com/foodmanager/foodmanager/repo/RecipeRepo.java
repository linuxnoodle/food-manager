package com.foodmanager.foodmanager.repo;

import com.foodmanager.foodmanager.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeRepo extends JpaRepository<Recipe, UUID> {

    List<Recipe> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
}
