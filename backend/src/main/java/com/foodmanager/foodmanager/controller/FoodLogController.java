package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.dto.LogEntryRequest;
import com.foodmanager.foodmanager.dto.LogEntryResponse;
import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.service.FoodLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * A user's food log. Auth is required — SecurityConfig's
 * anyRequest().authenticated() covers it, no extra rule needed. The current
 * user is pulled off the session cookie by
 * {@link com.foodmanager.foodmanager.config.SessionAuthFilter}.
 */
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class FoodLogController {

    private final FoodLogService foodLogService;

    @PostMapping
    public ResponseEntity<LogEntryResponse> addEntry(
            @AuthenticationPrincipal User user,
            @RequestBody LogEntryRequest req) {
        LogEntryResponse resp = foodLogService.addEntry(user, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public List<LogEntryResponse> listEntries(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            return foodLogService.listEntries(user);
        }
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return foodLogService.listEntriesForDay(user, start, end);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        foodLogService.deleteEntry(user, id);
    }
}
