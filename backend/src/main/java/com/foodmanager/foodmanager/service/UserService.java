package com.foodmanager.foodmanager.service;

import com.foodmanager.foodmanager.dto.UserRegistrationReq;
import com.foodmanager.foodmanager.dto.UserResponseDto;
import com.foodmanager.foodmanager.entity.User;
import com.foodmanager.foodmanager.exception.DuplicateEntityException;
import com.foodmanager.foodmanager.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j // logging
public class UserService {
    // Lombok auto constructor
    private final UserRepo userRepo;
    private final PasswordEncoder pwdEncoder;

    public UserResponseDto registerUser(UserRegistrationReq req) {
        log.info("registration email: {}", req.email());

        // check if user email or username is already taken
        // if so throw an exception to be handled and stop the user from registering
        if (userRepo.existsByEmail(req.email())) {
            throw new DuplicateEntityException("email already registered dumbo");
        }

        if (userRepo.existsByUsername(req.username())){
            throw new DuplicateEntityException("username already taken dumbo");
        }

        // create user object
        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());

        // hashes password so it isn't stored in plaintext
        // might be a good idea to add salting/peppering?
        user.setPassword(pwdEncoder.encode(req.password()));

        // save user to db
        User su = userRepo.save(user);
        log.info("user registered with ID: {}", su.getId());

        // move up to probably output response dto
        return new UserResponseDto(
          su.getId(),
          su.getUsername(),
          su.getEmail()
        );
    }
}
