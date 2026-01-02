package com.booking.userService.startup;

import com.booking.userService.model.Role;
import com.booking.userService.model.User;
import com.booking.userService.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private String adminEmail = "admin@example.com";

    private String adminPassword = "AdminPassword123";

    @Autowired
    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Check if the admin user already exists
        if (userRepository.findByEmail(adminEmail).isEmpty()) { 
            
            // If not, create a new admin user
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN) // Set the role to ADMIN
                    .enabled(true)
                    .build();
            
            userRepository.save(admin);
            
            log.info("Admin account created successfully with email: {}", adminEmail);
        } else {
            log.info("Admin account with email {} already exists. Skipping creation.", adminEmail);
        }
    }
}