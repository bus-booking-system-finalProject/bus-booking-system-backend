package com.booking.bookingService.controller;

import com.booking.bookingService.dto.feedback.FeedbackRequest;
import com.booking.bookingService.dto.feedback.FeedbackResponse;
import com.booking.bookingService.service.FeedbackService;
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

    // GET /feedback/me?tripId={tripId}
    @GetMapping("/me")
    public ResponseEntity<?> getMyFeedback(
            @RequestParam UUID tripId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User must be logged in");
        }

        FeedbackResponse data = feedbackService.getMyFeedbackForTrip(tripId, currentUser.getUsername());

        // We return 200 OK even if data is null (meaning "No review yet")
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data); 
        
        return ResponseEntity.ok(response);
    }

}