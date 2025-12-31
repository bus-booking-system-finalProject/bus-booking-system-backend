package com.booking.userService.controller;

import com.booking.userService.dto.AdminCreateUserRequest;
import com.booking.userService.dto.UserResponse;
import com.booking.userService.dto.UserStatusUpdateRequest;
import com.booking.userService.model.User;
import com.booking.userService.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.booking.userService.dto.AdminUpdateUserRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;

    @Autowired
    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // 1. GET ALL USERS
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        // Simple call - no need for stream().collect() if service returns List
        List<UserResponse> users = userService.getAllUsers();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", users);
        response.put("message", "All users retrieved successfully");
        
        return ResponseEntity.ok(response);
    }

    // 2. CREATE USER (Admin level)
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        User newUser = userService.createUserByAdmin(request);
        // Use the helper method from UserService to ensure consistent response format
        UserResponse responseDto = userService.mapToUserResponse(newUser);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", responseDto);
        response.put("message", "User created successfully by admin");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 3. ENABLE / DISABLE USER
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Long id,
            @RequestBody UserStatusUpdateRequest request
    ) {
        User updatedUser = userService.updateUserStatus(id, request.getEnabled());
        UserResponse responseDto = userService.mapToUserResponse(updatedUser);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", responseDto);
        response.put("message", "User status updated to " + (request.getEnabled() ? "Active" : "Disabled"));

        return ResponseEntity.ok(response);
    }

    // 4. EDIT USER (Admin level)
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequest request
    ) {
        User updatedUser = userService.updateUserByAdmin(id, request);
        UserResponse responseDto = userService.mapToUserResponse(updatedUser);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", responseDto);
        response.put("message", "User details updated successfully");

        return ResponseEntity.ok(response);
    }
}