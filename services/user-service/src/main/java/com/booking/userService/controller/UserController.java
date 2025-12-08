package com.booking.userService.controller;

import com.booking.userService.dto.LoginRequest;
import com.booking.userService.dto.RegisterRequest;
import com.booking.userService.dto.UserProfileResponse;
import com.booking.userService.dto.LoginResponse;
import com.booking.userService.dto.UserResponse; 
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

@RestController
@RequestMapping("/")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    // --- Refresh token validity (7 days) ---
    private final long REFRESH_TOKEN_VALIDITY_SECONDS = 604800;

    @Autowired
    public UserController(UserService userService, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User newUser = userService.registerUser(request); 
        
        // Reuse your UserResponse DTO for consistency
        UserResponse userProfileResp = new UserResponse(
                newUser.getId(),
                newUser.getEmail(),
                newUser.getRole(),
                newUser.getCreatedAt()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", new UserProfileResponse(userProfileResp)); // Return the full user profile
        response.put("message", "User registered successfully");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse servletResponse 
    ) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = (User) userService.loadUserByUsername(request.getEmail());
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Save refresh token to DB
        userService.saveUserRefreshToken(user, refreshToken);

        // --- Set cookies ---
        setSecureHttpOnlyCookie(servletResponse, "refreshToken", refreshToken, REFRESH_TOKEN_VALIDITY_SECONDS);

        // Return user profile
        UserResponse userResp = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );

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

                    UserResponse userResp = new UserResponse(
                            user.getId(),
                            user.getEmail(),
                            user.getRole(),
                            user.getCreatedAt()
                    );

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
    
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(
            @AuthenticationPrincipal UserDetails currentUserDetails 
    ) {
        User user = (User) userService.loadUserByUsername(currentUserDetails.getUsername());

        UserResponse userProfileResponse = new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", new UserProfileResponse(userProfileResponse));
        response.put("message", "User profile retrieved successfully");
        
        return ResponseEntity.ok(response);
    }

    // --- Helper methods ---

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
}