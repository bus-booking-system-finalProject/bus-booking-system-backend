package com.booking.bookingService.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class TicketDetailResponse {
    private UUID ticketId;
    private String ticketCode;
    private String userId;

    private String contactName;
    private String contactEmail;
    private String contactPhone;
    
    private String status;
    private List<String> seats;
    private TripDetailsDto tripDetails;
    private PricingDto pricing; // Tái sử dụng PricingDto
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    @Data @Builder
    public static class TripDetailsDto {
        private UUID tripId;
        private String route;
        private String operator;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;
    }
    
    @Data @Builder
    public static class PricingDto {
        private BigDecimal total;
        private String currency;
    }
}