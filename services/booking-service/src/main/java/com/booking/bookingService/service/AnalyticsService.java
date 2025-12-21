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

    // Helper to get valid sales statuses (CONFIRMED + COMPLETED)
    private List<String> getValidSalesStatuses() {
        return List.of(
            Ticket.TicketStatus.CONFIRMED.name(), 
            Ticket.TicketStatus.COMPLETED.name()
        );
    }

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

        // Update: Pass List of statuses instead of single string
        List<Object[]> rawResults = ticketRepository.findDailyTrends(
            getValidSalesStatuses(), 
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

        // Update: Pass List of statuses
        List<Object[]> results = ticketRepository.findPopularRoutes(
            getValidSalesStatuses(), 
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

        long totalSearches = searchLogRepository.countSearchesInRange(startDateTime, endDateTime);

        // Update: Count bookings where status is CONFIRMED OR COMPLETED
        long totalBookings = ticketRepository.count((root, query, cb) -> cb.and(
            root.get("status").in(List.of(Ticket.TicketStatus.CONFIRMED, Ticket.TicketStatus.COMPLETED)),
            cb.between(root.get("createdAt"), startDateTime, endDateTime)
        ));

        double rate = 0.0;
        if (totalSearches > 0) {
            rate = ((double) totalBookings / totalSearches) * 100;
        }

        return ConversionRateDto.builder()
                .totalSearches(totalSearches)
                .totalBookings(totalBookings)
                .conversionRatePercentage(Math.round(rate * 100.0) / 100.0)
                .build();
    }

    public AnalyticsSummaryDto getSummaryStats(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) fromDate = LocalDate.now().minusDays(30);
        if (toDate == null) toDate = LocalDate.now();

        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(LocalTime.MAX);

        List<Object[]> results = ticketRepository.getSummaryStats(startDateTime, endDateTime);

        if (results.isEmpty()) {
            return AnalyticsSummaryDto.builder().totalRevenue(BigDecimal.ZERO).build();
        }

        // Indices match the new Query in TicketRepository
        // 0:Total, 1:Confirmed, 2:Pending, 3:Cancelled, 4:Completed, 5:Revenue
        Object[] row = results.get(0);

        long total = ((Number) row[0]).longValue();
        long confirmed = row[1] != null ? ((Number) row[1]).longValue() : 0;
        long pending = row[2] != null ? ((Number) row[2]).longValue() : 0;
        long cancelled = row[3] != null ? ((Number) row[3]).longValue() : 0;
        long completed = row[4] != null ? ((Number) row[4]).longValue() : 0;
        BigDecimal revenue = (BigDecimal) row[5];

        return AnalyticsSummaryDto.builder()
                .totalTickets(total)
                .confirmedTickets(confirmed)
                .pendingTickets(pending)
                .cancelledTickets(cancelled)
                .completedTickets(completed) // New Field
                .totalRevenue(revenue)
                .build();
    }
}