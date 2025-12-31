package com.booking.userService.dto;

import com.booking.userService.model.Role;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor // Generates the full constructor with all 7 fields
@JsonInclude(JsonInclude.Include.NON_NULL) // <--- KEY CHANGE: Hides null fields in JSON
public class UserResponse {
    private Long id;
    private String email;
    private Role role;
    private Date createdAt;
    
    // New fields
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;

    private Boolean enabled;

    // --- MANUAL COMPATIBILITY CONSTRUCTOR ---
    // This allows your existing code (new UserResponse(id, email, role, date)) to work
    public UserResponse(Long id, String email, Role role, Date createdAt) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
        this.enabled = true;
        // New fields will be null automatically
    }
}