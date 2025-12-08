package com.booking.bookingService.controller;

import com.booking.bookingService.dto.BusRequest;
import com.booking.bookingService.dto.BusResponse;
import com.booking.bookingService.dto.SeatDefinition;
import com.booking.bookingService.model.Bus;
import com.booking.bookingService.service.BusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
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
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", busService.createBus(request));
        response.put("message", "Bus created successfully");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
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

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", busResponses);
        response.put("message", "Buses retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getBus(@PathVariable UUID id) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", busService.getBus(id));
        response.put("message", "Bus retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBus(@PathVariable UUID id, @Valid @RequestBody BusRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", busService.updateBus(id, request));
        response.put("message", "Bus updated successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBus(@PathVariable UUID id) {
        busService.deleteBus(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        response.put("message", "Bus deleted successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/seats/custom")
    public ResponseEntity<?> saveCustomSeatMap(
            @PathVariable UUID id, 
            @RequestBody List<SeatDefinition> seatDefinitions
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", busService.saveCustomSeatMap(id, seatDefinitions));
        response.put("message", "Custom seat map saved successfully");
        return ResponseEntity.ok(response);
    }
}