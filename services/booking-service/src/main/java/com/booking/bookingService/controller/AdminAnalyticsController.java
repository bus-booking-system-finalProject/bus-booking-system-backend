// src/main/java/com/booking/bookingService/controller/AdminAnalyticsController.java
package com.booking.bookingService.controller;

import com.booking.bookingService.dto.analytics.RevenueTrendDto;
import com.booking.bookingService.dto.analytics.PopularRouteDto;
import com.booking.bookingService.dto.analytics.ConversionRateDto;
import com.booking.bookingService.dto.analytics.AnalyticsSummaryDto;
import com.booking.bookingService.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    // API: GET /admin/analytics/trends?startDate=2025-12-01&endDate=2025-12-31
    // Access: Admin only
    @GetMapping("/trends")
    // @PreAuthorize("hasRole('ADMIN')") // Uncomment this when you are ready to enforce roles
    public ResponseEntity<?> getBookingTrends(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<RevenueTrendDto> trends = analyticsService.getBookingTrends(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", trends);
        response.put("message", "Booking trends retrieved successfully (Status: CANCELLED)");

        return ResponseEntity.ok(response);
    }

    // API: GET /admin/analytics/routes/popular?limit=5
    @GetMapping("/routes/popular")
    public ResponseEntity<?> getPopularRoutes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "5") int limit
    ) {
        List<PopularRouteDto> routes = analyticsService.getPopularRoutes(startDate, endDate, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", routes);
        response.put("message", "Popular routes retrieved successfully (Status: CANCELLED)");

        return ResponseEntity.ok(response);
    }

    // API: GET /admin/analytics/conversion
    @GetMapping("/conversion")
    public ResponseEntity<?> getConversionRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ConversionRateDto data = analyticsService.getConversionRate(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("message", "Conversion rate calculated successfully");

        return ResponseEntity.ok(response);
    }

    // API: GET /admin/analytics/summary
    @GetMapping("/summary")
    public ResponseEntity<?> getSummaryStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        AnalyticsSummaryDto summary = analyticsService.getSummaryStats(startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", summary);
        response.put("message", "Dashboard summary retrieved successfully");

        return ResponseEntity.ok(response);
    }
}