package com.booking.bookingService.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class TicketResponse {
    private UUID ticketId; // Đổi bookingId -> ticketId
    private String ticketCode;
    private UUID tripId;
    private String status;
    private List<String> seats;
    private int passengers;
    private PricingDto pricing;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;

    @Data @Builder
    public static class PricingDto {
        private BigDecimal subtotal;
        private BigDecimal serviceFee;
        private BigDecimal total;
        private String currency;
    }
}