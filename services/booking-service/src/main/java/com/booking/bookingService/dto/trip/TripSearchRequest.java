package com.booking.bookingService.dto.trip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripSearchRequest {
    // Required fields
    private String origin;
    private String destination;
    private Instant date; // UTC timestamp from frontend (current time if today, 00:00:00Z if future date)
    private String timezone; // Timezone from frontend (e.g., "Asia/Ho_Chi_Minh")

    // Optional fields with defaults
    @Builder.Default
    private Integer passengers = 1;

    private List<String> busTypes;
    private Boolean isLimousine;
    private Boolean hasWC;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime minDepartureTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime maxDepartureTime;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private String sort;

    private List<String> operators;

    // Pagination
    @Builder.Default
    private Integer page = 1;

    @Builder.Default
    private Integer limit = 20;

}