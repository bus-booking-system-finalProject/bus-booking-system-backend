package com.booking.bookingService.dto.trip;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripCreateRequest {
    
    @NotNull(message = "Route ID is required")
    private UUID routeId;

    private UUID busId;

    private UUID busModelId;

    @NotNull(message = "Departure time is required")
    @Future(message = "Departure time must be in the future")
    private LocalDateTime departureTime;

    @NotNull(message = "Base price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal originalPrice;

    private BigDecimal discountPrice;

    private String status;
}