package com.booking.bookingService.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BookingDetailResponse {
    private UUID bookingId;
    private String bookingReference; // Mã vé (VD: BK2025...)
    private String userId; // Email hoặc User ID
    private TripDetailsDto tripDetails;
    private List<PassengerDto> passengers;
    private BookingResponse.PricingDto pricing;
    private String status;
    private PaymentDto payment;
    private ETicketDto eTicket;
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
    public static class PassengerDto {
        private String fullName;
        private String documentId;
        private String seatCode;
    }

    @Data @Builder
    public static class PaymentDto {
        private String paymentId;
        private String status;
        private String method;
        private LocalDateTime paidAt;
    }

    @Data @Builder
    public static class ETicketDto {
        private String ticketUrl;
        private String qrCode;
    }
}