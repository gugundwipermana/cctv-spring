package com.homeserver.cctv.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.homeserver.cctv.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUsername(String username);
}
