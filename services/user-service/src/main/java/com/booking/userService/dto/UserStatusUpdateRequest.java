package com.booking.userService.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserStatusUpdateRequest {
    @NotNull(message = "Enabled status is required")
    private Boolean enabled;
}