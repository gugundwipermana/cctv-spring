package com.homeserver.cctv.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.homeserver.cctv.entity.User;
import com.homeserver.cctv.repository.UserRepository;

@Configuration
public class AdminSeederConfig {
    
    @Bean
    public CommandLineRunner seedAdminUser(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${cctv.admin.username:admin}") String adminUsername,
        @Value("${cctv.admin.password:admin}") String adminPassword
    ) {
        return args -> {
            if (userRepository.findByUsername(adminUsername).isPresent()) {
                System.out.println("Admin user already exists.");
                return;
            }
            if (adminPassword == null || adminPassword.isEmpty() || adminPassword.isBlank()) {
                System.out.println("Admin password is not set. Skipping admin user creation.");
                throw new IllegalStateException("Admin password is not set. Please set the 'cctv.admin.password' property.");
            }

            User admin = new User();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole("ADMIN");
            userRepository.save(admin);
            System.out.println("Admin user created with username: " + adminUsername);
        };
    }
}
