package com.booking.userService.dto;

import com.booking.userService.model.Role;
import lombok.Data;

@Data
public class AdminUpdateUserRequest {
    private String fullName;
    private String email;
    private String phoneNumber;
    private Role role;     // Admin can change roles
    private String avatarUrl;
}