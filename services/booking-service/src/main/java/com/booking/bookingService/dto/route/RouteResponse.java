package com.booking.bookingService.dto.route;

import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteResponse {
    private UUID id;
    private String name;
    private UUID operatorId;
    private String origin;
    private String destination;
    private int distanceKm;
    private int estimatedMinutes;
    private List<RouteStopResponse> stops;
}
