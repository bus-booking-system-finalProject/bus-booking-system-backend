package com.booking.bookingService.controller;

import com.booking.bookingService.dto.FeedbackRequest;
import com.booking.bookingService.dto.FeedbackResponse;
import com.booking.bookingService.service.FeedbackService;
import com.booking.bookingService.dto.OperatorReviewsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import java.util.UUID;

@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<?> createFeedback(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User must be logged in");
        }

        FeedbackResponse responseDto = feedbackService.createFeedback(request, currentUser.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", responseDto);
        response.put("message", "Feedback submitted successfully");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Get Operator Reviews (Public - NEW MOVED HERE)
    // Path: GET /feedback/operators/{operatorId}
    @GetMapping("/operators/{operatorId}")
    public ResponseEntity<?> getOperatorReviews(
            @PathVariable UUID operatorId,
            @RequestParam(defaultValue = "1") int page, // Default to Page 1
            @RequestParam(defaultValue = "20") int limit
    ) {
        // We now pass int page/limit directly to service, NOT Pageable
        OperatorReviewsResponse data = feedbackService.getReviewsForOperator(operatorId, page, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        response.put("message", "Operator reviews retrieved successfully");
        
        return ResponseEntity.ok(response);
    }
}