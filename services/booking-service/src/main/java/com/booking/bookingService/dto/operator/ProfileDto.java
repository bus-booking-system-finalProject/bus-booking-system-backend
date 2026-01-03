package com.booking.bookingService.dto.operator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileDto {
    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Phone number is required")
    private String contactPhone;

    private String image;
}