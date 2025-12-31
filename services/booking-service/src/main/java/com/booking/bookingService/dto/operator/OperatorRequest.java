package com.booking.bookingService.dto.operator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OperatorRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Phone number is required")
    private String contactPhone;

    private String image;
}