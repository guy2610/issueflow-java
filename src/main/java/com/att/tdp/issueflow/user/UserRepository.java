package com.att.tdp.issueflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByUsernameIgnoreCaseIn(List<String> usernames);
    List<User> findByRoleOrderByCreatedAtAsc(UserRole role);
}