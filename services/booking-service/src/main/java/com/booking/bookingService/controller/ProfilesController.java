package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator;
import com.booking.bookingService.service.OperatorService;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.operator.OperatorRequest;
import com.booking.bookingService.dto.operator.ProfileDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.booking.bookingService.model.Operator;
import java.util.UUID;

@RestController
@RequestMapping("/profiles")
@RequiredArgsConstructor
public class ProfilesController {
    private final OperatorService operatorService;

    @GetMapping
    public ResponseEntity<?> getCurrentOperator(@CurrentOperator UUID operatorId) {
        Operator operator = operatorService.getOperator(operatorId);
        return ResponseEntity.ok(ApiResponse.success(operator, "My Operator Profile retrieved"));
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProfileDto>> updateCurrentOperator(
            @CurrentOperator UUID operatorId,
            @RequestPart("operator") @Valid ProfileDto request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        OperatorRequest opRequest = OperatorRequest.builder()
            .contactEmail(request.getContactEmail())
            .contactPhone(request.getContactPhone())
            .image(request.getImage())
            .build();

        Operator updatedOperator = operatorService.updateOperator(operatorId, opRequest, file);
        ProfileDto profile = ProfileDto.builder()
            .contactEmail(updatedOperator.getContactEmail())
            .contactPhone(updatedOperator.getContactPhone())
            .image(updatedOperator.getImage())
            .build();

        return ResponseEntity.ok(ApiResponse.success(profile, "Profile updated successfully"));
    }
}