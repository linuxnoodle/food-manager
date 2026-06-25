package com.foodmanager.foodmanager.service;

import com.foodmanager.foodmanager.dto.LoginRequest;
import com.foodmanager.foodmanager.dto.LoginResponse;
import com.foodmanager.foodmanager.entity.Session;
import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.exception.InvalidCredentialsException;
import com.foodmanager.foodmanager.repo.SessionRepo;
import com.foodmanager.foodmanager.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j // logging
public class AuthService {
    private final UserRepo userRepo;
    private final SessionRepo sessionRepo;
    private final PasswordEncoder pwdEncoder;
    private final SecureRandom secureRandom = new SecureRandom(); // crypto strong rng for tokens

    public LoginResponse login(LoginRequest req) {
        // find by email or username, either works as the identifier
        User user = userRepo.findByEmail(req.identifier())
                .or(() -> userRepo.findByUsername(req.identifier()))
                .orElseThrow(() -> new InvalidCredentialsException("invalid username or password"));

        // check the password against the stored bcrypt hash
        // bcrypt already salts internally so no manual salting needed here
        if (!pwdEncoder.matches(req.password(), user.getPassword())) {
            throw new InvalidCredentialsException("invalid username or password");
        }

        // mint an opaque session token, 256 bits of randomness base64url encoded
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // store the session so we can verify it later, expires in a day
        Session session = new Session();
        session.setToken(token);
        session.setUser(user);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        sessionRepo.save(session);

        log.info("user logged in: {}", user.getEmail());

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail());
    }
}
