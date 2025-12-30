package com.booking.bookingService.controller;

import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.operator.OperatorReviewsResponse;
import com.booking.bookingService.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/reviews") // Separate top-level path
@RequiredArgsConstructor
public class ReviewController {

    private final FeedbackService feedbackService;

    // Path: GET /reviews/operators/{operatorId}
    @GetMapping("/{operatorId}")
    public ResponseEntity<?> getOperatorReviews(
            @PathVariable UUID operatorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        OperatorReviewsResponse data = feedbackService.getReviewsForOperator(operatorId, page, limit);

        return ResponseEntity.ok(ApiResponse.success(data, "Operator reviews retrieved successfully"));
    }
}