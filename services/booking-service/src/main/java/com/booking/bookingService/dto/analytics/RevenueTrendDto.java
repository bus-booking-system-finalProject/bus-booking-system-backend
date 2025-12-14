// src/main/java/com/booking/bookingService/dto/analytics/RevenueTrendDto.java
package com.booking.bookingService.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTrendDto {
    private String date; // Format: YYYY-MM-DD
    private BigDecimal totalRevenue;
    private Long bookingCount;
}