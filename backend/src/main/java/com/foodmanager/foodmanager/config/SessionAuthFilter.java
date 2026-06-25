package com.foodmanager.foodmanager.config;

import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.repo.SessionRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

// turns the session cookie into an authenticated principal on each request
public class SessionAuthFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "session"; // matches the cookie set on login

    private final SessionRepo sessionRepo;

    public SessionAuthFilter(SessionRepo sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = readCookie(request);
        if (token != null) {
            // look the token up, only accept it while it is still valid
            sessionRepo.findByToken(token).ifPresent(session -> {
                if (session.getExpiresAt().isAfter(Instant.now())) {
                    User user = session.getUser();
                    var auth = new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        }
        // no token or invalid -> leave context empty, the authz rules decide (401)
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
