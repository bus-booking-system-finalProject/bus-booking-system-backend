package com.booking.bookingService.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class TicketRequest {
    @NotNull(message = "Trip ID is required")
    private UUID tripId;

    @NotEmpty(message = "Seats must not be empty")
    private List<String> seats;

    @NotBlank(message = "Contact name is required")
    private String contactName;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    @JsonProperty("isGuestCheckout")
    private boolean isGuestCheckout;
}