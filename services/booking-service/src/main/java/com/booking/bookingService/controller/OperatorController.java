package com.booking.bookingService.controller;

import com.booking.bookingService.service.OperatorService;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.operator.OperatorRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.booking.bookingService.model.Operator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/operators")
@RequiredArgsConstructor
public class OperatorController {
    private final OperatorService operatorService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Operator>> createOperator(@Valid @RequestPart("operator") OperatorRequest request, @RequestPart(value = "file", required = false) MultipartFile file) {
        return new ResponseEntity<>(
            ApiResponse.success(operatorService.createOperator(request, file), "Operator created successfully"),
            HttpStatus.CREATED
        );
    }

    @GetMapping
    public ResponseEntity<?> getAllOperators() {
        return ResponseEntity.ok(ApiResponse.success(operatorService.getAllOperators(), "Operators retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOperator(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(operatorService.getOperator(id), "Operator retrieved successfully"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Operator>> updateOperator(
            @PathVariable UUID id, 
            @RequestPart("operator") @Valid OperatorRequest request, // Use @RequestPart for JSON
            @RequestPart(value = "file", required = false) MultipartFile file // Use @RequestPart for File
    ) {
        Operator updatedOperator = operatorService.updateOperator(id, request, file);
        return ResponseEntity.ok(ApiResponse.success(updatedOperator, "Operator updated successfully"));
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