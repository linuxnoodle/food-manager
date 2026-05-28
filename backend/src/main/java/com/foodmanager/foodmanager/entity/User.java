package com.foodmanager.foodmanager.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity // tells SpringBoot that this is an entity class
@Table(name = "users") // decl this as a table for db, using H2 for now
@NoArgsConstructor // adds following to all final vars
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username, email;

    @Column(nullable = false)
    private String password; // probably bcrypt2 pwd
}