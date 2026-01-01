package com.booking.bookingService.dto.route;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteResponse {
    private UUID id;
    
    private DetailsDto details;

    private OperatorDto operator;

    @JsonProperty("pickup_points")
    private List<StopDto> pickupPoints;

    @JsonProperty("dropoff_points")
    private List<StopDto> dropoffPoints;

    private StopDto from;
    private StopDto to;

    @Data @Builder
    public static class OperatorDto {
        private UUID id;
        private String name;
        private String image;
    }

    @Data @Builder
    public static class StopDto {
        private UUID id;
        private String name;
        private String address;
        private int duration;
    }

    @Data @Builder
    public static class DetailsDto {
        private String name;
        private String origin;
        private String destination;
        private int distanceKm;
        private int estimatedMinutes;
    }
}
