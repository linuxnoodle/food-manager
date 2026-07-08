package com.foodmanager.foodmanager.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per "I ate this" event. Points at a cached {@link Food} by OFF code,
 * so we can scale the per-100g nutrients by how much was eaten. Index on
 * (user_id, logged_at) keeps the "what did I eat today" query fast.
 * <p>
 * quantity is always grams or ml — they scale the same against per-100g data,
 * so unit is really just there for the UI to label things.
 */
@Entity
@Table(name = "food_log_entries",
        indexes = {
                @Index(name = "idx_fle_user_logged", columnList = "user_id, logged_at")
        })
@NoArgsConstructor
@Getter
@Setter
public class FoodLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "food_code", nullable = false)
    private Food food;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private String meal;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (loggedAt == null) loggedAt = now;
    }
}
