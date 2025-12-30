package com.booking.bookingService.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Data
@Builder
public class TripSearchResponse {
    private UUID tripId;
    private RouteDto route;
    private OperatorDto operator;
    private BusDto bus;
    private ScheduleDto schedules;
    private PricingDto pricing;
    private int duration;
    private StopDto from;
    private StopDto to;
    private AvailabilityDto availability;
    private String status;

    @Data @Builder
    public static class RouteDto {
        private String name;
        private int durationMinutes;

        @JsonProperty("pickup_points")
        private List<StopDto> pickupPoints;

        @JsonProperty("dropoff_points")
        private List<StopDto> dropoffPoints;
    }

    @Data @Builder
    public static class OperatorDto {
        private UUID id;
        private String name;
        private String image;
        private OperatorRating ratings;
    }

    @Data @Builder
    public static class OperatorRating {
        private Double overall;
        private int reviews;
    }

    @Data @Builder
    public static class BusDto {
        private String model;
        private String type;
    }

    @Data @Builder
    public static class ScheduleDto {
        private String hour;
        private String minute;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;
    }

    @Data @Builder
    public static class PricingDto {
        private BigDecimal original;
        private BigDecimal discount;
    }

    @Data @Builder
    public static class AvailabilityDto {
        private int totalSeats;
        private int availableSeats;
    }

    @Data @Builder
    public static class StopDto {
        private UUID stopId;
        private String name;
        private String address;
        private int duration;
    }
}