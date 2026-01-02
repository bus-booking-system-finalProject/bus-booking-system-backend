package com.booking.bookingService.dto.route;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RouteRequest {
    @NotNull(message = "Name is required")
    private String name;

    @NotBlank(message = "Origin is required")
    private String origin;

    @NotBlank(message = "Destination is required")
    private String destination;

    @Min(value = 1, message = "Distance must be positive")
    private int distanceKm;

    @Min(value = 1, message = "Estimated minutes must be positive")
    private int estimatedMinutes;

    private List<RouteStopRequest> pickupStops;
    private List<RouteStopRequest> dropoffStops;

    @NotNull(message = "Route active status must be specified")
    private Boolean isActive;
}