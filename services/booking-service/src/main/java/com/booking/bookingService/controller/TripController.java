package com.booking.bookingService.controller;

import com.booking.bookingService.dto.SeatMapResponse;
import com.booking.bookingService.dto.TripRequest;
import com.booking.bookingService.dto.TripSearchResponse;
import com.booking.bookingService.dto.TripSearchRequest;
import com.booking.bookingService.service.TripService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<?> createTrip(@Valid @RequestBody TripRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", tripService.createTrip(request));
        response.put("message", "Trip created successfully");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<?> updateTrip(@PathVariable UUID tripId, @Valid @RequestBody TripRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", tripService.updateTrip(tripId, request));
        response.put("message", "Trip updated successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<?> deleteTrip(@PathVariable UUID tripId) {
        tripService.deleteTrip(tripId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        response.put("message", "Trip deleted successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchTrips(
        @ModelAttribute TripSearchRequest request
    ) {
        Page<TripSearchResponse> result = tripService.searchTrips(request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result.getContent());
        response.put("message", "Trips retrieved successfully");
        
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", request.getPage());
        pagination.put("limit", request.getLimit());
        pagination.put("total", result.getTotalElements());
        pagination.put("totalPages", result.getTotalPages());
        
        response.put("pagination", pagination);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<Map<String, Object>> getTripDetail(@PathVariable UUID tripId) {
        TripSearchResponse trip = tripService.getTripById(tripId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", trip);
        response.put("message", "Trip details retrieved successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{tripId}/seats")
    public ResponseEntity<Map<String, Object>> getSeatMap(@PathVariable UUID tripId) {
        SeatMapResponse seatMap = tripService.getSeatMap(tripId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", seatMap);
        response.put("message", "Seat map retrieved successfully");

        return ResponseEntity.ok(response);
    }
}