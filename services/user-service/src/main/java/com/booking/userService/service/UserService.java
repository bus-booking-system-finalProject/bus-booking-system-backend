package com.booking.userService.service;

import com.booking.userService.model.User;
import com.booking.userService.repository.UserRepository;
import com.booking.userService.dto.RegisterRequest;
import com.booking.userService.exception.EmailAlreadyExistsException;
import com.booking.userService.model.Role;
import com.booking.userService.dto.UserResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

import com.booking.userService.dto.UpdateProfileRequest;
import com.booking.userService.dto.AdminCreateUserRequest;
import com.booking.userService.dto.AdminUpdateUserRequest;
import com.booking.userService.dto.ChangePasswordRequest;
import org.springframework.security.authentication.BadCredentialsException;

import com.booking.userService.model.VerificationToken;
import com.booking.userService.repository.VerificationTokenRepository;
import com.booking.userService.exception.InvalidTokenException;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    private final UserDetailsServiceImpl userDetailsService;

    @Autowired
    private VerificationTokenRepository tokenRepository;
    
    @Autowired
    private EmailService emailService;

    @Autowired
    public UserService(
            UserRepository userRepository, 
            PasswordEncoder passwordEncoder,
            UserDetailsServiceImpl userDetailsService 
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    public User registerUser(RegisterRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            throw new EmailAlreadyExistsException("Email " + request.getEmail() + " already taken");
        });

        User newUser = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .enabled(false) // <--- DISABLED BY DEFAULT
                .build();
        
        User savedUser = userRepository.save(newUser);
        
        // Generate Token
        VerificationToken token = new VerificationToken(savedUser);
        tokenRepository.save(token);
        
        // Send Email (Async)
        // NOTE: In production, change localhost to your Frontend URL
        String verifyUrl = "http://localhost:5173/user/verify-email?token=" + token.getToken();
        emailService.sendEmail(savedUser.getEmail(), "Account Verification", "Click here to verify: " + verifyUrl);

        return savedUser;
    }

    // 2. VERIFY EMAIL
    public void verifyEmail(String token) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));
        
        if (verificationToken.isExpired()) {
            throw new InvalidTokenException("Token expired");
        }
        
        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        
        tokenRepository.delete(verificationToken); // Cleanup
    }

    // 3. FORGOT PASSWORD
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found")); // Or suppress error for security

        // Check for existing token and delete it? Or reuse. Let's create new.
        tokenRepository.findByUser(user).ifPresent(tokenRepository::delete);

        VerificationToken token = new VerificationToken(user);
        tokenRepository.save(token);

        String resetUrl = "http://localhost:5173/user/reset-password?token=" + token.getToken();
        emailService.sendEmail(email, "Reset Password", "Click here to reset: " + resetUrl);
    }

    // 4. RESET PASSWORD
    public void resetPassword(String token, String newPassword) {
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (verificationToken.isExpired()) {
            throw new InvalidTokenException("Token expired");
        }

        User user = verificationToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        tokenRepository.delete(verificationToken);
    }
    
    /**
     * This method is used by the AuthenticationManager to load the user
     * for login/password checks.
     */
    public UserDetails loadUserByUsername(String email) {
        // This is now correct and will no longer cause an error
        return userDetailsService.loadUserByUsername(email);
    }

    // --- Method to save the refresh token ---
    public User saveUserRefreshToken(User user, String refreshToken) {
        user.setRefreshToken(refreshToken);
        return userRepository.save(user);
    }

    // --- Method to find user by refresh token ---
    public Optional<User> findByRefreshToken(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken);
    }

    public void deleteRefreshToken(String token) {
        // 1. Find the user who owns this token
        Optional<User> userOptional = userRepository.findByRefreshToken(token);
        
        // 2. If user exists, set their token to null (Revoke it)
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setRefreshToken(null); // Clear the token
            userRepository.save(user);  // Update the DB
        }
    }

    // --- NEW: Update User Profile ---
    public User updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        return userRepository.save(user);
    }

    // --- NEW: Change Password ---
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 1. Check if the old password is correct
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadCredentialsException("Old password does not match");
        }

        // 2. Encode and set the new password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // --- UPDATED: getAllUsers to include new fields in response ---
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    public User createUserByAdmin(AdminCreateUserRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            throw new EmailAlreadyExistsException("Email " + request.getEmail() + " already exists");
        });

        User newUser = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(request.getRole()) // Set role explicitly
                .enabled(true)
                .build();

        return userRepository.save(newUser);
    }

    // --- NEW: Toggle User Status (Ban/Unban) ---
    public User updateUserStatus(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setEnabled(enabled);
        
        // If disabling, you might want to revoke tokens immediately (optional enhancement)
        if (!enabled) {
            user.setRefreshToken(null); 
        }
        
        return userRepository.save(user);
    }

    // Helper to map User -> UserResponse consistently
    public UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getAvatarUrl(),
                user.isEnabled()
        );
    }

    // --- NEW: Admin Update Any User ---
    public User updateUserByAdmin(Long userId, AdminUpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        
        // Handle sensitive fields cautiously
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            // Check if new email is taken by someone else
            userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new EmailAlreadyExistsException("Email " + request.getEmail() + " already exists");
                }
            });
            user.setEmail(request.getEmail());
        }

        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }

        return userRepository.save(user);
    }
}