package com.booking.bookingService.dto.ticket;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class TicketCancelResponse {
    private UUID ticketId;
    private String status;
    private RefundDto refund;
    private LocalDateTime cancelledAt;

    @Data @Builder
    public static class RefundDto {
        private BigDecimal amount;
        private int percentage;
        private String status;
        private String processingTime;
        private String refundMethod;
    }
}