package com.foodmanager.foodmanager.service;

import com.foodmanager.foodmanager.dto.LogEntryRequest;
import com.foodmanager.foodmanager.dto.LogEntryResponse;
import com.foodmanager.foodmanager.entity.Food;
import com.foodmanager.foodmanager.entity.FoodLogEntry;
import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.exception.FoodNotFoundException;
import com.foodmanager.foodmanager.exception.InvalidSearchQueryException;
import com.foodmanager.foodmanager.repo.FoodLogEntryRepo;
import com.foodmanager.foodmanager.repo.FoodRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Append-only food log. On add we resolve the food through {@link FoodService}
 * (it auto-fetches + caches from OFF on a miss), then store a row with the
 * per-100g nutrients scaled by how much was eaten.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FoodLogService {

    private static final Set<String> ALLOWED_MEALS = Set.of("breakfast", "lunch", "dinner", "snack");
    private static final Set<String> ALLOWED_UNITS = Set.of("g", "ml");

    private final FoodLogEntryRepo logRepo;
    private final FoodService foodService;
    private final FoodRepo foodRepo;

    @Transactional
    public LogEntryResponse addEntry(User user, LogEntryRequest req) {
        validate(req);

        // ensure the food is cached (auto-fetches from OFF on a cache miss)
        foodService.getByCode(req.code(), false);
        Food food = foodRepo.findById(req.code())
                .orElseThrow(() -> new InvalidSearchQueryException("food not resolvable: " + req.code()));

        FoodLogEntry entry = new FoodLogEntry();
        entry.setUser(user);
        entry.setFood(food);
        entry.setQuantity(req.quantity());
        entry.setUnit(req.unit());
        entry.setMeal(req.meal());
        entry.setLoggedAt(req.loggedAt() != null ? req.loggedAt() : Instant.now());
        logRepo.save(entry);

        log.info("user {} logged food {} ({} {})", user.getId(), req.code(), req.quantity(), req.unit());
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public List<LogEntryResponse> listEntries(User user) {
        return logRepo.findByUserIdOrderByLoggedAtDesc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LogEntryResponse> listEntriesForDay(User user, Instant start, Instant end) {
        return logRepo.findByUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(user.getId(), start, end).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteEntry(User user, UUID id) {
        FoodLogEntry entry = logRepo.findById(id)
                .orElseThrow(() -> new FoodNotFoundException("log entry: " + id));
        if (!entry.getUser().getId().equals(user.getId())) {
                throw new FoodNotFoundException("log entry: " + id);
        }
        logRepo.delete(entry);
    }

    private void validate(LogEntryRequest req) {
        if (req == null) throw new InvalidSearchQueryException("body is required");
        if (req.code() == null || req.code().isBlank()) {
            throw new InvalidSearchQueryException("code is required");
        }
        if (req.quantity() <= 0) {
            throw new InvalidSearchQueryException("quantity must be > 0");
        }
        if (req.unit() == null || !ALLOWED_UNITS.contains(req.unit())) {
            throw new InvalidSearchQueryException("invalid unit (use one of " + ALLOWED_UNITS + ")");
        }
        if (req.meal() == null || !ALLOWED_MEALS.contains(req.meal())) {
            throw new InvalidSearchQueryException("invalid meal (use one of " + ALLOWED_MEALS + ")");
        }
    }

    // scaled nutrition = per-100g value * quantity / 100
    private LogEntryResponse toResponse(FoodLogEntry e) {
        Food f = e.getFood();
        double factor = e.getQuantity() / 100.0;
        return new LogEntryResponse(
                e.getId(),
                f.getCode(),
                f.getName(),
                f.getImageUrl(),
                e.getQuantity(),
                e.getUnit(),
                e.getMeal(),
                e.getLoggedAt(),
                scale(f.getKcal(), factor),
                scale(f.getProteinG(), factor),
                scale(f.getFatG(), factor),
                scale(f.getCarbsG(), factor),
                scale(f.getSugarG(), factor),
                scale(f.getSaltG(), factor)
        );
    }

    private Double scale(Double v, double factor) {
        return v == null ? null : v * factor;
    }
}
