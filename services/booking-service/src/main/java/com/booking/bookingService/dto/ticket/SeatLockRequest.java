package com.booking.bookingService.dto.ticket;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class SeatLockRequest {
    private UUID tripId;
    private List<String> seats;
    private String sessionId; // Quan trọng: Dùng cho khách vãng lai (Guest) chưa login
}