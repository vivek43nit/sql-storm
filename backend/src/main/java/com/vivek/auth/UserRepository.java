package com.vivek.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<FkBlitzUser, Long> {
    Optional<FkBlitzUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
