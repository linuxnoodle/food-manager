package com.foodmanager.foodmanager.repo;

import com.foodmanager.foodmanager.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionRepo extends JpaRepository<Session, UUID> {
    // lookups
    Optional<Session> findByToken(String token);
}
