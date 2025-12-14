// src/main/java/com/booking/bookingService/dto/analytics/ConversionRateDto.java
package com.booking.bookingService.dto.analytics;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ConversionRateDto {
    private long totalSearches;
    private long totalBookings;
    private double conversionRatePercentage;
}