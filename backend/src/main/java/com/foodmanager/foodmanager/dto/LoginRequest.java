package com.foodmanager.foodmanager.dto;

// inbound login req, identifier can be username or email
public record LoginRequest(String identifier, String password) {}
