package com.foodmanager.foodmanager.dto;

import java.util.UUID;

// outbound login resp, carries the session token
public record LoginResponse(String token, UUID id, String username, String email) {}
