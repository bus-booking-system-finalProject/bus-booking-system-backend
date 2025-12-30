package com.booking.bookingService.dto.bus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class BusRequest {
    @NotNull(message = "Operator ID is required")
    private UUID operatorId;

    @NotBlank(message = "Plate number is required")
    private String plateNumber;

    @NotBlank(message = "Model is required")
    private String model;

    @NotBlank(message = "Type is required (Standard, Sleeper, Limousine)")
    private String type;

    @Min(value = 1, message = "Capacity must be at least 1")
    private int seatCapacity;
}