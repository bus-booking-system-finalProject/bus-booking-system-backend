package com.booking.bookingService.dto.bus;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

@Data
public class BusRequest {
    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotNull(message = "Bus model ID must be provided")
    private UUID busModelId;

    @NotNull(message = "Bus active status must be specified")
    private Boolean isActive;
}