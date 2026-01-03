package com.booking.userService.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    @Pattern(
        regexp = "^[A-Za-z]+(?: [A-Za-z]+)*$",
        message = "Full name can only contain letters and spaces"
    )
    private String fullName;

    @Pattern(
        regexp = "^\\+?[0-9]{10,15}$",
        message = "Phone number must be valid (10-15 digits, optional +)"
    )
    private String phoneNumber;

    private String avatarUrl;
}
