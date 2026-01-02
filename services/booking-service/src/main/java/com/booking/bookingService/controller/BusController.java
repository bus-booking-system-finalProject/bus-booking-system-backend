package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.bus.BusRequest;
import com.booking.bookingService.dto.bus_model.BusModelRequest;
import com.booking.bookingService.service.BusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/buses")
@RequiredArgsConstructor
public class BusController {
    private final BusService busService;

    // --- Bus Models (Templates) ---

    // 1. Create Model (Must include seatDefinitions)
    @PostMapping("/models")
    public ResponseEntity<?> createBusModel(
            @Valid @RequestBody BusModelRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(busService.createBusModel(request, operatorId), "Bus Model created successfully"));
    }

    @GetMapping("/models")
    public ResponseEntity<?> getMyBusModels(@CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(busService.getMyBusModels(operatorId), "Bus Models retrieved"));
    }

    @GetMapping("/models/{modelId}")
    public ResponseEntity<?> getBusModelDetails(
            @PathVariable UUID modelId,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(busService.getBusModelDetails(modelId, operatorId), "Bus Model Details retrieved successfully"));
    }

    @PutMapping("/models/{modelId}")
    public ResponseEntity<?> updateBusModel(
            @PathVariable UUID modelId, 
            @Valid @RequestBody BusModelRequest request,
            @CurrentOperator UUID operatorId
    ) {
        Object result = busService.updateBusModel(modelId, request, operatorId);
        return ResponseEntity.ok(ApiResponse.success(result, "Seat map updated successfully"));
    }


    // --- Physical Buses ---

    @PostMapping
    public ResponseEntity<?> createBus(
            @Valid @RequestBody BusRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.status(201).body(ApiResponse.success(busService.createBus(request, operatorId), "Bus created successfully"));
    }

    @GetMapping
    public ResponseEntity<?> getMyBuses(@CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(busService.getAllBuses(operatorId), "Buses retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBus(
            @PathVariable UUID id, 
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(busService.getBusDetails(id, operatorId), "Bus retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBus(
            @PathVariable UUID id, 
            @Valid @RequestBody BusRequest request,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(busService.updateBus(id, request, operatorId), "Bus updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBus(
            @PathVariable UUID id, 
            @CurrentOperator UUID operatorId
    ) {
        busService.deleteBus(id, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Bus deleted successfully"));
    }
}