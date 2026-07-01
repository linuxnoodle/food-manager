package com.foodmanager.foodmanager.config;

import com.foodmanager.foodmanager.repo.SessionRepo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRepo sessionRepo) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // sameSite=Strict on the cookie covers csrf for us
            .cors(cors -> {}) // picks up the mvc cors config in CorsConfig
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // we keep sessions in the db, not in memory
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/user/register").permitAll()
                .requestMatchers("/h2-console/**").permitAll() // dev only, h2 web console
                .anyRequest().authenticated()
            )
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin())) // h2 console is iframe-based
            .addFilterBefore(new SessionAuthFilter(sessionRepo), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                // 401 as application/problem+json so it matches the rest of the api
                res.setStatus(HttpStatus.UNAUTHORIZED.value());
                res.setContentType("application/problem+json");
                res.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"invalid or missing session\"}");
            }));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
