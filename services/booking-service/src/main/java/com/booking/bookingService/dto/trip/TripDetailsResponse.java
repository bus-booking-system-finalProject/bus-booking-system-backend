package com.booking.bookingService.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TripDetailsResponse {
    private UUID id;

    private RouteDto route;

    private BusDto bus;

    private BusModelDto busModel;

    private LocalDateTime departureTime;

    private BigDecimal originalPrice;

    private BigDecimal discountPrice;

    private String status;

    private int availableSeats;

    private SeatMapDto seatMap;

    @Data @Builder
    public static class RouteDto {
        private UUID id;
        private String name;
    }

    @Data @Builder
    public static class BusModelDto {
        private UUID id;
        private String name;
        private String typeDisplay;
    }

    @Data @Builder
    public static class BusDto {
        private UUID id;
        private String name;
        private String plateNumber;
        private BusModelDto busModel;
    }

    @Data @Builder
    public static class SeatMapDto {
        private int totalDecks;
        private int gridRows;
        private int gridColumns;
        private List<SeatDetailDto> seats;
    }

    @Data @Builder
    public static class SeatDetailDto {
        private String seatCode;
        private String status;
        private int row;
        private int col;
        private int deck;
        private PassengerDto passenger;
    }

    @Data @Builder
    public static class PassengerDto {
        private String name;
        private String email;
        private String phone;
    }
}