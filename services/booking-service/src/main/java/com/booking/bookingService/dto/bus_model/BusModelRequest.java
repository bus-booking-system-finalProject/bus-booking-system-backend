package com.booking.bookingService.dto.bus_model;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import java.util.List;

@Data
public class BusModelRequest {
    @NotBlank(message = "Model name is required")
    private String name;

    @Min(value = 1, message = "Total decks must be at least 1")
    private int totalDecks;

    @Min(value = 1, message = "Grid rows must be at least 1")
    private int gridRows;

    @Min(value = 1, message = "Grid columns must be at least 1")
    private int gridColumns;
    
    @NotBlank(message = "Bus type (SLEEPER, SEATER, CABIN) is required")
    private String type;

    @NotNull(message = "Limousine status must be specified")
    private Boolean isLimousine;

    @NotNull(message = "WC availability must be specified")
    private Boolean hasWC;

    @NotEmpty(message = "Seat map definitions are required")
    @NotNull(message = "Seat map cannot be null")
    private List<SeatDto> seats;
}