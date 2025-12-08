package com.booking.bookingService.controller;

import com.booking.bookingService.dto.OperatorRequest;
import com.booking.bookingService.service.OperatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/operators")
@RequiredArgsConstructor
public class OperatorController {
    private final OperatorService operatorService;

    @PostMapping
    public ResponseEntity<?> createOperator(@Valid @RequestBody OperatorRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", operatorService.createOperator(request));
        response.put("message", "Operator created successfully");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<?> getAllOperators() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", operatorService.getAllOperators());
        response.put("message", "Operators retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOperator(@PathVariable UUID id) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", operatorService.getOperator(id));
        response.put("message", "Operator retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateOperator(@PathVariable UUID id, @Valid @RequestBody OperatorRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", operatorService.updateOperator(id, request));
        response.put("message", "Operator updated successfully");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOperator(@PathVariable UUID id) {
        operatorService.deleteOperator(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", null);
        response.put("message", "Operator deleted successfully");
        return ResponseEntity.ok(response);
    }
}