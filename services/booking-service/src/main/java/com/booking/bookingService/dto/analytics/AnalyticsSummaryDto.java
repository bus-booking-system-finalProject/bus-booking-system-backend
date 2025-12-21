// src/main/java/com/booking/bookingService/dto/analytics/AnalyticsSummaryDto.java
package com.booking.bookingService.dto.analytics;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class AnalyticsSummaryDto {
    private long totalTickets;
    private long confirmedTickets;
    private long completedTickets; // Past bookings (Finished)
    private long pendingTickets;
    private long cancelledTickets;
    private BigDecimal totalRevenue;
}