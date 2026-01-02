package com.booking.bookingService.dto.bus_model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SeatDto {
    @NotBlank(message = "Seat code is required")
    private String code; // Operator's custom code: "A1", "VIP-1", "X99"

    @Min(1)
    private int row;    // Grid Row
    
    @Min(1)
    private int col;    // Grid Column
    
    @Min(1)
    private int deck; // Deck number
}
