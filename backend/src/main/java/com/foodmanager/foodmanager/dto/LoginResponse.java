package com.foodmanager.foodmanager.dto;

import java.util.UUID;

// outbound login resp, no token here on purpose, it rides in the cookie
public record LoginResponse(UUID id, String username, String email) {}
