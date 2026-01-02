package com.booking.bookingService.dto.schedule;
import lombok.Data;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.time.DayOfWeek;

@Data
public class TripScheduleRequest {
    private UUID routeId;
    private UUID busModelId;
    private LocalTime departureTime;
    private Set<DayOfWeek> daysOfWeek; // e.g. ["MONDAY", "FRIDAY"]
    private boolean active;
}