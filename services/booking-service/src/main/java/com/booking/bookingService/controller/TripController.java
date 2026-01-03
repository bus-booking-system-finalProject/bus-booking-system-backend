package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.ticket.SeatMapResponse;
import com.booking.bookingService.dto.trip.TripCreateRequest;
import com.booking.bookingService.dto.trip.TripCreateResponse;
import com.booking.bookingService.dto.trip.TripSearchRequest;
import com.booking.bookingService.dto.trip.TripSearchResponse;
import com.booking.bookingService.dto.trip.admin.TripSearchParams;
import com.booking.bookingService.service.TripService;
import com.booking.bookingService.service.SearchLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
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
            @Valid @RequestBody TripCreateRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tripService.createTrip(request, operatorId), "Trip created successfully"));
    }

    @PutMapping("/{tripId}")
    public ResponseEntity<?> updateTrip(
            @PathVariable UUID tripId, 
            @Valid @RequestBody TripCreateRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(tripService.updateTrip(tripId, request, operatorId), "Trip updated successfully"));
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<?> deleteTrip(
            @PathVariable UUID tripId,
            @CurrentOperator UUID operatorId
    ) {
        tripService.deleteTrip(tripId, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Trip deleted successfully"));
    }

    @GetMapping("/{tripId}/details")
    public ResponseEntity<?> getDetailsTrip(
            @PathVariable UUID tripId, 
            @CurrentOperator UUID operatorId
    ) {
        // Logic: Identify that the trip belongs to THIS operator
        // Returns TripCreateResponse (or a management-specific DTO) with sensitive data like plate numbers
        return ResponseEntity.ok(ApiResponse.success(tripService.getTripDetailsForOperator(tripId, operatorId), "Trip management details retrieved"));
    }

    @GetMapping
    public ResponseEntity<?> getMyTrips(
            @ModelAttribute TripSearchParams params,
            @CurrentOperator UUID operatorId
    ) {
        // Pass params to service
        List<TripCreateResponse> trips = tripService.getOperatorTrips(operatorId, params);
        return ResponseEntity.ok(ApiResponse.success(trips, "Trips retrieved successfully"));
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