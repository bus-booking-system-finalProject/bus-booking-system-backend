package com.booking.bookingService.dto.route;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RouteStopRequest {
    private UUID stationId;
    private int duration;
    private boolean isOrigin;
    private boolean isDestination;
}
