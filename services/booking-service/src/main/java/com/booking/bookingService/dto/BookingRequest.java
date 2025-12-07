package com.booking.bookingService.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class BookingRequest {
    @NotNull(message = "Trip ID is required")
    private UUID tripId;

    @NotEmpty(message = "Seats must not be empty")
    private List<String> seats;

    @NotEmpty(message = "Passenger list must not be empty")
    @Valid
    private List<PassengerRequest> passengers;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    private boolean isGuestCheckout;
}