package com.booking.bookingService.controller;

import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.bus.BusRequest;
import com.booking.bookingService.dto.bus.BusResponse;
import com.booking.bookingService.dto.ticket.SeatDefinition;
import com.booking.bookingService.model.Bus;
import com.booking.bookingService.service.BusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@RestController
@RequestMapping("/buses")
@RequiredArgsConstructor
public class BusController {
    private final BusService busService;

    @PostMapping
    public ResponseEntity<?> createBus(@Valid @RequestBody BusRequest request) {
        Bus createdBus = busService.createBus(request);
        return new ResponseEntity<>(
            ApiResponse.success(createdBus, "Bus created successfully"), 
            HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<?> getAllBuses() {
        List<Bus> buses = busService.getAllBuses();
        
        List<BusResponse> busResponses = buses.stream().map(bus -> 
            BusResponse.builder()
                .id(bus.getId())
                .operatorId(bus.getOperator().getId())
                .operatorName(bus.getOperator().getName())
                .plateNumber(bus.getPlateNumber())
                .model(bus.getModel())
                .type(bus.getType())
                .seatCapacity(bus.getSeatCapacity())
                .build()
        ).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(busResponses, "Buses retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBus(@PathVariable UUID id) {
        Bus bus = busService.getBus(id);
        return ResponseEntity.ok(ApiResponse.success(bus, "Bus retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBus(@PathVariable UUID id, @Valid @RequestBody BusRequest request) {
        Bus updatedBus = busService.updateBus(id, request);
        return ResponseEntity.ok(ApiResponse.success(updatedBus, "Bus updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBus(@PathVariable UUID id) {
        busService.deleteBus(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Bus deleted successfully"));
    }

    @PostMapping("/{id}/seats/custom")
    public ResponseEntity<?> saveCustomSeatMap(
            @PathVariable UUID id, 
            @RequestBody List<SeatDefinition> seatDefinitions
    ) {
        Object result = busService.saveCustomSeatMap(id, seatDefinitions);
        return ResponseEntity.ok(ApiResponse.success(result, "Custom seat map saved successfully"));
    }
}