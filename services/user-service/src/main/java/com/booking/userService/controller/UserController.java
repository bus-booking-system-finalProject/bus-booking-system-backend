package com.booking.userService.controller;

import com.booking.userService.dto.*;
import com.booking.userService.service.JwtService;
import com.booking.userService.service.UserService;
import com.booking.userService.model.User;
import jakarta.servlet.http.HttpServletResponse; 
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile; // Make sure this is imported

@RestController
@RequestMapping("/")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    private final long REFRESH_TOKEN_VALIDITY_SECONDS = 604800; // 7 days

    @Autowired
    public UserController(UserService userService, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    // --- HELPER: Maps User entity to UserResponse DTO ---
    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt(),
                user.getFullName(),    // Auto-mapped if exists
                user.getPhoneNumber(), // Auto-mapped if exists
                user.getAvatarUrl(),    // Auto-mapped if exists
                user.isEnabled()
        );
    }

    // ==================================================================
    // 1. PUBLIC AUTH ENDPOINTS
    // ==================================================================

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User newUser = userService.registerUser(request); 
        UserResponse userProfileResp = mapToUserResponse(newUser);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", new UserProfileResponse(userProfileResp));
        response.put("message", "User registered successfully");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse 
    ) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        var user = (User) userService.loadUserByUsername(request.getEmail());
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        userService.saveUserRefreshToken(user, refreshToken);
        setSecureHttpOnlyCookie(servletResponse, "refreshToken", refreshToken, REFRESH_TOKEN_VALIDITY_SECONDS);

        UserResponse userResp = mapToUserResponse(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", new LoginResponse(accessToken, userResp));
        response.put("message", "Login successfully");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @CookieValue(name = "refreshToken") String requestRefreshToken,
            HttpServletResponse servletResponse
    ) {
        return userService.findByRefreshToken(requestRefreshToken)
                .filter(user -> jwtService.isTokenValid(requestRefreshToken, user))
                .map(user -> {
                    String newAccessToken = jwtService.generateAccessToken(user);
                    String newRefreshToken = jwtService.generateRefreshToken(user);

                    userService.saveUserRefreshToken(user, newRefreshToken);
                    setSecureHttpOnlyCookie(servletResponse, "refreshToken", newRefreshToken, REFRESH_TOKEN_VALIDITY_SECONDS);

                    UserResponse userResp = mapToUserResponse(user);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("data", new LoginResponse(newAccessToken, userResp));
                    response.put("message", "Token refreshed successfully");

                    return ResponseEntity.ok((Object) response);
                })
                .orElseGet(() -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("data", null);
                    errorResponse.put("message", "Invalid or expired refresh token");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
                });
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
        HttpServletResponse response,
        @CookieValue(name = "refreshToken", required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            userService.deleteRefreshToken(refreshToken);
        }
        clearCookie(response, "refreshToken");
        
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", null);
        body.put("message", "Logged out successfully");

        return ResponseEntity.ok(body);
    }

    // ==================================================================
    // 2. AUTHENTICATED USER ENDPOINTS
    // ==================================================================

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal UserDetails currentUserDetails) {
        User user = (User) userService.loadUserByUsername(currentUserDetails.getUsername());
        UserResponse userProfileResponse = mapToUserResponse(user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", new UserProfileResponse(userProfileResponse));
        response.put("message", "User profile retrieved successfully");
        
        return ResponseEntity.ok(response);
    }

    // --- NEW: Update Profile (Phase 1) ---
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestBody UpdateProfileRequest request
    ) {
        // currentUser.getUsername() is the email
        User updatedUser = userService.updateUserProfile(currentUser.getUsername(), request);
        UserResponse responseDto = mapToUserResponse(updatedUser);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", new UserProfileResponse(responseDto));
        response.put("message", "Profile updated successfully");

        return ResponseEntity.ok(response);
    }

    // --- NEW: Upload Avatar Endpoint ---
    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadAvatar(
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            // Call the service method you just wrote
            String fileUrl = userService.uploadAvatar(currentUser.getUsername(), file);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", fileUrl);
            response.put("message", "Avatar uploaded successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // --- NEW: Change Password (Phase 1) ---
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetails currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(currentUser.getUsername(), request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        response.put("message", "Password changed successfully");

        return ResponseEntity.ok(response);
    }

    // ==================================================================
    // 3. COOKIE HELPERS
    // ==================================================================

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void setSecureHttpOnlyCookie(HttpServletResponse response, String name, String value, long maxAgeInSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .maxAge(maxAgeInSeconds)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // 1. VERIFY EMAIL (Public)
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        userService.verifyEmail(token);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Email verified successfully. You can now login.");
        return ResponseEntity.ok(response);
    }

    // 2. FORGOT PASSWORD (Public)
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.getEmail());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "If your email exists, a reset link has been sent.");
        return ResponseEntity.ok(response);
    }

    // 3. RESET PASSWORD (Public)
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Password has been reset successfully.");
        return ResponseEntity.ok(response);
    }
}