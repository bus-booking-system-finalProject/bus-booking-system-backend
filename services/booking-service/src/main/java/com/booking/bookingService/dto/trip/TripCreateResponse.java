package com.booking.bookingService.dto.trip;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TripCreateResponse {
    private UUID id;

    private RouteDto route;

    private BusDto bus;

    private BusModelDto busModel;

    private LocalDateTime departureTime;

    private BigDecimal originalPrice;

    private BigDecimal discountPrice;

    private String status;

    private int availableSeats;

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
        private String plateNumber;
        private BusModelDto busModel;
    }
}