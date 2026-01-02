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

    @PostMapping
    public ResponseEntity<?> createRoute(
            @Valid @RequestBody RouteRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(routeService.createRoute(request, operatorId), "Route created successfully"));
    }

    @GetMapping
    public ResponseEntity<?> getMyRoutes(@CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(routeService.getAllRoutes(operatorId), "Routes retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoute(
            @PathVariable UUID id, 
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(routeService.getRoute(id, operatorId), "Route retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoute(
            @PathVariable UUID id, 
            @Valid @RequestBody RouteRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(routeService.updateRoute(id, request, operatorId), "Route updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(
            @PathVariable UUID id, 
            @CurrentOperator UUID operatorId
    ) {
        routeService.deleteRoute(id, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Route deleted successfully"));
    }
}