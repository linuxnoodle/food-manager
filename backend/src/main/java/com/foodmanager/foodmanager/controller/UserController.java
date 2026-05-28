package com.foodmanager.foodmanager.controller;

import com.foodmanager.foodmanager.dto.UserRegistrationReq;
import com.foodmanager.foodmanager.dto.UserResponseDto;
import com.foodmanager.foodmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController // routing
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService; // handle actual requests

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> registerUser(@RequestBody UserRegistrationReq req){
        UserResponseDto resp = userService.registerUser(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp); // should automatically return 201 on created
    }
}
