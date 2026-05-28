package com.foodmanager.foodmanager.dto;

// inbound req
public record UserRegistrationReq(String username, String email, String password) {}