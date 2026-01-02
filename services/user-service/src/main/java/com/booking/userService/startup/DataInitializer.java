package com.booking.userService.startup;

import com.booking.userService.client.BookingClient;
import com.booking.userService.dto.ApiResponse;
import com.booking.userService.dto.OperatorDto;
import com.booking.userService.model.Role;
import com.booking.userService.model.User;
import com.booking.userService.repository.UserRepository;

import java.util.List;
import java.util.UUID;

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
    private final BookingClient bookingClient;

    private String adminEmail = "admin@example.com";

    private String adminPassword = "AdminPassword123";

    @Autowired
    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, BookingClient bookingClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bookingClient = bookingClient;
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

        try {
            ApiResponse<List<OperatorDto>> response = bookingClient.getAllOperators();
            
            if (response.isSuccess() && response.getData() != null) {
                List<OperatorDto> operators = response.getData();
                log.info("Retrieved {} operators: {}", operators.size(), operators);

                UUID phuongTrangId = operators.stream()
                        .filter(op -> "Phương Trang".equalsIgnoreCase(op.getName()))
                        .map(OperatorDto::getId)
                        .findFirst()
                        .orElse(null);

                if (phuongTrangId != null) {
                    createDefaultOperator(phuongTrangId);
                    createDefaultStaff(phuongTrangId);
                } else {
                    log.warn("Operator 'Phương Trang' not found in retrieved list.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch operators: {}", e.getMessage());
        }
    }

    private void createDefaultOperator(UUID operatorId) {
        String email = "operator_pt@vexesieure.com";
        if (userRepository.findByEmail(email).isEmpty()) {
            User operator = new User();
            operator.setEmail(email);
            operator.setPassword(passwordEncoder.encode("password123"));
            operator.setFullName("Phuong Trang Admin");
            operator.setRole(Role.OPERATOR);
            operator.setOperatorId(operatorId); // Set ID
            operator.setEnabled(true);
            userRepository.save(operator);
            System.out.println("Default Operator created: " + email);
        }
    }

    private void createDefaultStaff(UUID operatorId) {
        String email = "staff_pt@vexesieure.com";
        if (userRepository.findByEmail(email).isEmpty()) {
            User staff = new User();
            staff.setEmail(email);
            staff.setPassword(passwordEncoder.encode("password123"));
            staff.setFullName("Phuong Trang Staff");
            staff.setRole(Role.STAFF);
            staff.setOperatorId(operatorId); // Set ID
            staff.setEnabled(true);
            userRepository.save(staff);
            System.out.println("Default Staff created: " + email);
        }
    }
}