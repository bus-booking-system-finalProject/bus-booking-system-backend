package com.booking.bookingService.dto.trip.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripSearchParams {
    private String origin;
    private String destination;
    private LocalDate date;
}