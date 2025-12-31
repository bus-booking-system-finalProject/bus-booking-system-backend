package com.booking.bookingService.dto.ticket;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SeatDefinition {
    @NotBlank(message = "Seat code is required")
    private String seatCode; // Operator's custom code: "A1", "VIP-1", "X99"

    @Min(1)
    private int row;    // Grid Row
    
    @Min(1)
    private int col;    // Grid Column
    
    @Min(1)
    private int deck = 1; // Deck number

    private String type; // Optional override (e.g., this specific seat is VIP)
}