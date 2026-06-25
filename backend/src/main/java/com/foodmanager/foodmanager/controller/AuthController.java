package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.dto.LoginRequest;
import com.foodmanager.foodmanager.dto.LoginResponse;
import com.foodmanager.foodmanager.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // routing
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService; // handle actual requests

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req){
        LoginResponse resp = authService.login(req);
        return ResponseEntity.status(HttpStatus.OK).body(resp);
    }
}
