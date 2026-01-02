package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.ticket.SeatMapResponse;
import com.booking.bookingService.dto.trip.TripRequest;
import com.booking.bookingService.dto.trip.TripSearchRequest;
import com.booking.bookingService.dto.trip.TripSearchResponse;
import com.booking.bookingService.model.Trip;
import com.booking.bookingService.service.TripService;
import com.booking.bookingService.service.SearchLogService;

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
    private final SearchLogService searchLogService;

    // --- OPERATOR ENDPOINTS (Protected) ---

    @PostMapping
    public ResponseEntity<?> createTrip(
            @Valid @RequestBody TripRequest request,
            @CurrentOperator UUID operatorId
    ) {
        Trip trip = tripService.createTrip(request, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(trip, "Trip created successfully"));
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<?> updateTrip(
            @PathVariable UUID tripId, 
            @Valid @RequestBody TripRequest request,
            @CurrentOperator UUID operatorId
    ) {
        Trip trip = tripService.updateTrip(tripId, request, operatorId);
        return ResponseEntity.ok(ApiResponse.success(trip, "Trip updated successfully"));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<?> deleteTrip(
            @PathVariable UUID tripId,
            @CurrentOperator UUID operatorId
    ) {
        tripService.deleteTrip(tripId, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Trip deleted successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyTrips(@CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(tripService.getOperatorTrips(operatorId), "Trips retrieved successfully"));
    }

    // --- PUBLIC ENDPOINTS (Customers) ---

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchTrips(
        @ModelAttribute TripSearchRequest request
    ) {
        searchLogService.logSearchEvent(request);
        
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
    public ResponseEntity<?> getTripDetail(@PathVariable UUID tripId) {
        TripSearchResponse trip = tripService.getTripById(tripId);
        
        return ResponseEntity.ok(ApiResponse.success(trip, "Trip details retrieved successfully"));
    }

    @GetMapping("/{tripId}/seats")
    public ResponseEntity<?> getSeatMap(@PathVariable UUID tripId) {
        SeatMapResponse seatMap = tripService.getSeatMap(tripId);

        return ResponseEntity.ok(ApiResponse.success(seatMap, "Seat map retrieved successfully"));
    }
}