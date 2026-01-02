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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/buses")
@RequiredArgsConstructor
public class BusController {
    private final BusService busService;

    // --- Bus Models (Templates) ---

    // 1. Create Model (Must include seatDefinitions)
    @PostMapping(value = "/models", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createBusModel(
            @Valid @RequestBody BusModelRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @CurrentOperator UUID operatorId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(busService.createBusModel(request, images, operatorId), "Bus Model created successfully"));
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

    @PutMapping(value = "/models/{modelId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateBusModel(
            @PathVariable UUID modelId, 
            @RequestPart("model") @Valid BusModelRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @CurrentOperator UUID operatorId
    ) {
        Object result = busService.updateBusModel(modelId, request, images, operatorId);
        return ResponseEntity.ok(ApiResponse.success(result, "Bus Model updated successfully"));
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