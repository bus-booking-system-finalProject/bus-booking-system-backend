package com.booking.bookingService.dto.ticket;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SeatMapResponse {
    private UUID tripId;
    private int totalDecks;     // Helpful for frontend
    private int gridRows;       // Max rows (height)
    private int gridColumns;    // Max cols (width)
    private List<SeatDto> seats;

    @Data @Builder
    public static class SeatDto {
        private String seatId;
        private String seatCode;
        private String status;
        private BigDecimal price;
        private int deck;
        
        // Coordinates
        private int row;
        private int col;
    }
}