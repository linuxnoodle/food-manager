package com.foodmanager.foodmanager.dto;

import java.util.UUID;

// outbound dto
public record UserResponseDto(UUID id, String username, String email) {}