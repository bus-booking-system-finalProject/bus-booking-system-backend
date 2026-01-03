package com.booking.userService.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Old password is required")
    private String oldPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters long")
    private String newPassword;

    // --- NEW VALIDATION CHECK ---
    @AssertTrue(message = "New password must be different from the old password")
    public boolean isPasswordDifferent() {
        // If either is null, we skip this check (let @NotBlank handle it)
        if (oldPassword == null || newPassword == null) {
            return true;
        }
        // Return true if they are DIFFERENT (Valid)
        // Return false if they are SAME (Invalid)
        return !oldPassword.equals(newPassword);
    }
}