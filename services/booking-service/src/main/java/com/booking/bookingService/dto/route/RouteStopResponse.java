package com.booking.bookingService.dto.route;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;
import com.booking.bookingService.Enum.StopType;

@Data
@Builder
public class RouteStopResponse {
    private UUID id;
    private UUID stationId;
    private String name;
    private String address;
    private StopType type;
    private int duration;
    private boolean isOrigin;
    private boolean isDestination;
}
