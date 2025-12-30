package com.booking.bookingService.controller;

import com.booking.bookingService.dto.route.RouteRequest;
import com.booking.bookingService.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/routes")
@RequiredArgsConstructor
public class RouteController {
    private final RouteService routeService;

    @PostMapping
    public ResponseEntity<?> createRoute(@Valid @RequestBody RouteRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", routeService.createRoute(request));
        response.put("message", "Route created successfully");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<?> getAllRoutes() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", routeService.getAllRoutes());
        response.put("message", "Routes retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRoute(@PathVariable UUID id) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", routeService.getRoute(id));
        response.put("message", "Route retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoute(@PathVariable UUID id, @Valid @RequestBody RouteRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", routeService.updateRoute(id, request));
        response.put("message", "Route updated successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoute(@PathVariable UUID id) {
        routeService.deleteRoute(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        response.put("message", "Route deleted successfully");
        return ResponseEntity.ok(response);
    }
}