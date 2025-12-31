package com.booking.bookingService.dto.route;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;
import com.booking.bookingService.Enum.StopType;

@Data
@Builder
public class RouteStopRequest {
    private UUID stationId;
    private StopType type;
    private int duration;
    private boolean isOrigin;
    private boolean isDestination;
}
