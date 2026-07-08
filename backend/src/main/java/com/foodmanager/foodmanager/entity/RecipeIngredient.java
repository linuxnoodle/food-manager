package com.foodmanager.foodmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One ingredient line of a {@link Recipe}. Points at a cached {@link Food} by
 * OFF code, with a {@code quantity} in grams/ml. This is the owning side of the
 * recipe &lt;-&gt; ingredient link.
 */
@Entity
@Table(name = "recipe_ingredients")
@NoArgsConstructor
@Getter
@Setter
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(optional = false)
    @JoinColumn(name = "food_code", nullable = false)
    private Food food;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private String unit;
}
