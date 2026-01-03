package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.route.RouteRequest;
import com.booking.bookingService.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {
    private final RouteService routeService;

    // Public search endpoint for routes
    @GetMapping("/search")
    public ResponseEntity<?> searchRoutes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination) {
        if (origin != null && destination != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    routeService.searchByOriginAndDestination(origin, destination),
                    "Routes found successfully"));
        }
        if (keyword != null && !keyword.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    routeService.searchRoutes(keyword),
                    "Routes found successfully"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("Please provide keyword or origin/destination"));
    }

    // Operator search endpoint for their own routes
    @GetMapping("/my/search")
    public ResponseEntity<?> searchMyRoutes(
            @RequestParam String keyword,
            @CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(
                routeService.searchOperatorRoutes(keyword, operatorId),
                "Routes found successfully"));
    }

    @PostMapping
    public ResponseEntity<?> createRoute(
            @Valid @RequestBody RouteRequest request,
            @CurrentOperator UUID operatorId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(routeService.createRoute(request, operatorId), "Route created successfully"));
    }

    @GetMapping
    public ResponseEntity<?> getMyRoutes(@CurrentOperator UUID operatorId) {
        return ResponseEntity
                .ok(ApiResponse.success(routeService.getAllRoutes(operatorId), "Routes retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoute(
            @PathVariable UUID id,
            @CurrentOperator UUID operatorId) {
        return ResponseEntity
                .ok(ApiResponse.success(routeService.getRoute(id, operatorId), "Route retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoute(
            @PathVariable UUID id,
            @Valid @RequestBody RouteRequest request,
            @CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(
                ApiResponse.success(routeService.updateRoute(id, request, operatorId), "Route updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(
            @PathVariable UUID id,
            @CurrentOperator UUID operatorId) {
        routeService.deleteRoute(id, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Route deleted successfully"));
    }
}