package com.foodmanager.foodmanager.repo;

import com.foodmanager.foodmanager.entity.FoodLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FoodLogEntryRepo extends JpaRepository<FoodLogEntry, UUID> {

    List<FoodLogEntry> findByUserIdOrderByLoggedAtDesc(UUID userId);

    List<FoodLogEntry> findByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(UUID userId, Instant start, Instant end);
}
