package com.booking.bookingService.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class TicketLookupResponse {
    private UUID ticketId;
    private String ticketCode;
    private String status;
    
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    
    private List<String> seats;
    private PricingDto pricing;
    
    // Nested Trip Details for UI display
    private TripDetailsDto tripDetails;

    private LocalDateTime createdAt;

    @Data @Builder
    public static class PricingDto {
        private BigDecimal total;
        private String currency;
    }

    @Data @Builder
    public static class TripDetailsDto {
        private UUID tripId;
        private String route;
        private String operator;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;
    }
}
