package com.booking.bookingService.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BookingHistoryResponse {
    private UUID bookingId;
    private String bookingReference;
    private TripSummaryDto trip;
    private List<String> seats;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;

    @Data @Builder
    public static class TripSummaryDto {
        private String route;
        private LocalDateTime departureTime;
        private String operator;
    }
}