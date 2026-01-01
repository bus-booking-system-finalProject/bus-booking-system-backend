package com.booking.bookingService.controller;

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

    @PostMapping
    public ResponseEntity<?> createStation(@Valid @RequestBody StationRequest request) {
        StationResponse response = stationService.createStation(request);
        return new ResponseEntity<>(ApiResponse.success(response, "Station created successfully"), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<?> getAllStations() {
        List<StationResponse> stations = stationService.getAllStations();
        return ResponseEntity.ok(ApiResponse.success(stations, "Stations retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStation(@PathVariable UUID id) {
        StationResponse station = stationService.getStation(id);
        return ResponseEntity.ok(ApiResponse.success(station, "Station retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStation(@PathVariable UUID id, @Valid @RequestBody StationRequest request) {
        StationResponse updated = stationService.updateStation(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Station updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStation(@PathVariable UUID id) {
        stationService.deleteStation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Station deleted successfully"));
    }
}