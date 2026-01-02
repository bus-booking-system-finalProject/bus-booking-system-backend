package com.booking.bookingService.dto.trip;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripRequest {
    
    @NotNull(message = "Route ID is required")
    private UUID routeId;

    @NotNull(message = "Bus ID is required")
    private UUID busId;

    @NotNull(message = "Departure time is required")
    @Future(message = "Departure time must be in the future")
    private LocalDateTime departureTime;

    @NotNull(message = "Arrival time is required")
    @Future(message = "Arrival time must be in the future")
    private LocalDateTime arrivalTime;

    @NotNull(message = "Base price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal originalPrice;

    private BigDecimal discountPrice;

    private String status; // OPTIONAL: SCHEDULED (default), CANCELLED, COMPLETED, DELAYED

    private Integer delayMinutes; // OPTIONAL: Only used when status is DELAYED

    // If null/empty, the system will use the default RouteStops.
    // If provided, the system will use this list exactly (Add/Delete/Reorder).
    private List<TripStopDto> stops;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TripStopDto {
        @NotNull(message = "Station ID is required")
        private UUID stationId;

        private String type; // "PICKUP" or "DROPOFF"

        @Min(0)
        private int orderIndex; // 0, 1, 2...

        @Min(0)
        private int timeOffsetMinutes; // Minutes after Trip Departure Time
    }
}