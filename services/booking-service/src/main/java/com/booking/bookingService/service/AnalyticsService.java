// src/main/java/com/booking/bookingService/service/AnalyticsService.java
package com.booking.bookingService.service;

import com.booking.bookingService.dto.analytics.RevenueTrendDto;
import com.booking.bookingService.dto.analytics.PopularRouteDto;
import com.booking.bookingService.dto.analytics.AnalyticsSummaryDto;
import com.booking.bookingService.model.Ticket;
import com.booking.bookingService.repository.TicketRepository;
import com.booking.bookingService.dto.analytics.ConversionRateDto;
import com.booking.bookingService.repository.SearchLogRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TicketRepository ticketRepository;
    private final SearchLogRepository searchLogRepository;

    /**
     * Get booking trends (Revenue & Count) grouped by day.
     * Currently filtering by CANCELLED status for testing.
     */
    @Transactional(readOnly = true)
    public List<RevenueTrendDto> getBookingTrends(LocalDate fromDate, LocalDate toDate) {
        // 1. Default to last 30 days if null
        if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
        if (toDate == null) toDate = LocalDate.now();

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);

        // 2. Call Repository (Using CANCELLED as requested)
        List<Object[]> rawResults = ticketRepository.findDailyTrends(
            Ticket.TicketStatus.CANCELLED.name(), 
            startDateTime, 
            endDateTime
        );

        // 3. Map Object[] to DTO
        List<RevenueTrendDto> trends = new ArrayList<>();
        for (Object[] row : rawResults) {
            // Native query returns java.sql.Date or java.lang.String depending on driver
            String dateStr = row[0].toString(); 
            BigDecimal revenue = (BigDecimal) row[1];
            Long count = ((Number) row[2]).longValue();

            trends.add(RevenueTrendDto.builder()
                .date(dateStr)
                .totalRevenue(revenue)
                .bookingCount(count)
                .build());
        }

        return trends;
    }

    /**
     * Get top popular routes based on booking count.
     */
    @Transactional(readOnly = true)
    public List<PopularRouteDto> getPopularRoutes(LocalDate fromDate, LocalDate toDate, int limit) {
        if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
        if (toDate == null) toDate = LocalDate.now();

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);

        // Using "CANCELLED" status for your testing environment
        // In production, change this to Ticket.TicketStatus.CONFIRMED.name()
        String status = Ticket.TicketStatus.CANCELLED.name();

        List<Object[]> results = ticketRepository.findPopularRoutes(
            status, 
            startDateTime, 
            endDateTime, 
            limit
        );

        List<PopularRouteDto> popularRoutes = new ArrayList<>();
        for (Object[] row : results) {
            String origin = (String) row[0];
            String destination = (String) row[1];
            Long count = ((Number) row[2]).longValue();
            BigDecimal revenue = (BigDecimal) row[3];

            popularRoutes.add(PopularRouteDto.builder()
                .routeName(origin + " → " + destination)
                .totalBookings(count)
                .totalRevenue(revenue)
                .build());
        }

        return popularRoutes;
    }

    public ConversionRateDto getConversionRate(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
        if (toDate == null) toDate = LocalDate.now();

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);

        // 1. Get Total Searches
        long totalSearches = searchLogRepository.countSearchesInRange(startDateTime, endDateTime);

        // 2. Get Total Bookings (Using CONFIRMED status ideally, but CANCELLED for your test)
        // Note: We can reuse findAll with a Spec, or add a simple count method in TicketRepository
        // Let's add a quick count method in TicketRepository logic or use existing count.
        long totalBookings = ticketRepository.count((root, query, cb) -> cb.and(
            cb.equal(root.get("status"), Ticket.TicketStatus.CANCELLED), // Change to CONFIRMED for prod
            cb.between(root.get("createdAt"), startDateTime, endDateTime)
        ));

        // 3. Calculate Rate
        double rate = 0.0;
        if (totalSearches > 0) {
            rate = ((double) totalBookings / totalSearches) * 100;
        }

        return ConversionRateDto.builder()
                .totalSearches(totalSearches)
                .totalBookings(totalBookings)
                .conversionRatePercentage(Math.round(rate * 100.0) / 100.0) // Round to 2 decimals
                .build();
    }

    public AnalyticsSummaryDto getSummaryStats(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
        if (toDate == null) toDate = LocalDate.now();

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);

        // FIX: Handle List<Object[]> return type
        List<Object[]> results = ticketRepository.getSummaryStats(startDateTime, endDateTime);

        if (results.isEmpty()) {
            return AnalyticsSummaryDto.builder()
                .totalRevenue(BigDecimal.ZERO)
                .build();
        }

        // Extract the first row
        Object[] row = results.get(0);

        // Safe Casting
        long total = ((Number) row[0]).longValue();
        // Handle potential nulls from SUM() if no records exist (though COALESCE usually handles it, CASE might return null)
        long confirmed = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long pending = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long cancelled = row[3] != null ? ((Number) row[3]).longValue() : 0;
        BigDecimal revenue = (BigDecimal) row[4];

        return AnalyticsSummaryDto.builder()
                .totalTickets(total)
                .confirmedTickets(confirmed)
                .pendingTickets(pending)
                .cancelledTickets(cancelled)
                .totalRevenue(revenue)
                .build();
    }
}