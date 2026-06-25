package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.config.SessionAuthFilter;
import com.foodmanager.foodmanager.dto.LoginRequest;
import com.foodmanager.foodmanager.dto.LoginResponse;
import com.foodmanager.foodmanager.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController // routing
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService; // handle actual requests

    // false for local http dev, override to true behind https in prod
    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req, HttpServletResponse response){
        AuthService.LoginResult result = authService.login(req);

        // hand the token back as an httponly cookie so js can't read it
        ResponseCookie cookie = ResponseCookie.from(SessionAuthFilter.COOKIE_NAME, result.token())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofDays(1))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());

        // body carries the user, never the token
        return ResponseEntity.status(HttpStatus.OK).body(
                new LoginResponse(result.id(), result.username(), result.email()));
    }
}
