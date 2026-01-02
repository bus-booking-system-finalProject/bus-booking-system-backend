package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator; // Import the new annotation
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.station.StationRequest;
import com.booking.bookingService.dto.station.StationResponse;
import com.booking.bookingService.service.StationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    // 1. Add @CurrentOperator UUID operatorId to arguments
    @PostMapping
    public ResponseEntity<?> createStation(
            @Valid @RequestBody StationRequest request, 
            @CurrentOperator UUID operatorId
    ) {
        // 2. Pass the ID to the service
        StationResponse response = stationService.createStation(request, operatorId);
        return new ResponseEntity<>(ApiResponse.success(response, "Station created successfully"), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<?> getMyStations(@CurrentOperator UUID operatorId) {
        // Reuse the ID here easily
        List<StationResponse> stations = stationService.getAllStations(operatorId);
        return ResponseEntity.ok(ApiResponse.success(stations, "Stations retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStation(@PathVariable UUID id, @CurrentOperator UUID operatorId) {
        StationResponse station = stationService.getStation(id, operatorId);
        return ResponseEntity.ok(ApiResponse.success(station, "Station retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStation(
            @PathVariable UUID id, 
            @Valid @RequestBody StationRequest request,
            @CurrentOperator UUID operatorId
    ) {
        StationResponse updated = stationService.updateStation(id, request, operatorId);
        return ResponseEntity.ok(ApiResponse.success(updated, "Station updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStation(@PathVariable UUID id, @CurrentOperator UUID operatorId) {
        stationService.deleteStation(id, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Station deleted successfully"));
    }
}