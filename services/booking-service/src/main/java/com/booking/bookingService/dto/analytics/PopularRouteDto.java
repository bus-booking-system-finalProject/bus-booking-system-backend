// src/main/java/com/booking/bookingService/dto/analytics/PopularRouteDto.java
package com.booking.bookingService.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularRouteDto {
    private String routeName; // e.g., "Ho Chi Minh - Da Lat"
    private Long totalBookings;
    private BigDecimal totalRevenue;
}