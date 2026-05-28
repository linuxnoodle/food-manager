package com.foodmanager.foodmanager.repo;

import com.foodmanager.foodmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepo extends JpaRepository<User, UUID> {
    //lookups
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    //exists
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
