package com.foodmanager.foodmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One row per OFF product. The OFF canonical code (a barcode internally) is the
 * natural primary key. The user never sees "barcode" as a concept — it's an opaque
 * ID returned by /api/food/search and consumed by /api/food/{code}.
 * <p>
 * Cache-control fields ({@code lastFetchedAt}, {@code etag}, {@code lastModified},
 * {@code deleted}) are first-class columns. {@code lowerName} is denormalized so
 * local ILIKE search works without a function index (portable to Postgres later).
 */
@Entity
@Table(name = "foods",
        indexes = {
                @Index(name = "idx_foods_lower_name", columnList = "lower_name"),
                @Index(name = "idx_foods_last_fetched", columnList = "last_fetched_at")
        })
@NoArgsConstructor
@Getter
@Setter
public class Food {

    @Id
    @Column(length = 24)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "lower_name", nullable = false)
    private String lowerName;

    private String brand;
    private String quantity;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "nutriscore_grade")
    private String nutriscoreGrade;

    @Column(name = "nova_group")
    private Integer novaGroup;

    @Column(name = "ingredients_text", length = 8000)
    private String ingredientsText;

    /**
     * Canonical taxonomy tags like "en:chicken". Stored locally so /api/food/local-search
     * can filter over the cache without calling OFF. The idx_fi_tag index makes that fast.
     */
    @ElementCollection
    @CollectionTable(name = "food_ingredients",
            joinColumns = @JoinColumn(name = "food_code"),
            indexes = @Index(name = "idx_fi_tag", columnList = "tag"))
    @Column(name = "tag", length = 128)
    private Set<String> ingredientsTags = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "food_allergens",
            joinColumns = @JoinColumn(name = "food_code"))
    @Column(name = "tag", length = 128)
    private Set<String> allergens = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "food_additives",
            joinColumns = @JoinColumn(name = "food_code"))
    @Column(name = "tag", length = 128)
    private Set<String> additives = new HashSet<>();

    // per-100g nutrients, nullable — not every product reports every value
    private Double kcal;

    @Column(name = "protein_g")
    private Double proteinG;

    @Column(name = "fat_g")
    private Double fatG;

    @Column(name = "carbs_g")
    private Double carbsG;

    @Column(name = "sugar_g")
    private Double sugarG;

    @Column(name = "salt_g")
    private Double saltG;

    // --- cache control ---

    @Column(name = "last_fetched_at", nullable = false)
    private Instant lastFetchedAt;

    @Column(length = 512)
    private String etag;                    // from OFF "ETag" header, nullable

    @Column(name = "last_modified")
    private String lastModified;            // from OFF "Last-Modified" header, nullable

    @Column(nullable = false)
    private boolean deleted = false;        // tombstone if OFF returned 404 on a refresh

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (lastFetchedAt == null) lastFetchedAt = now;
        if (name == null) name = "";
        lowerName = name.toLowerCase(Locale.ROOT);
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (name != null) lowerName = name.toLowerCase(Locale.ROOT);
    }
}
