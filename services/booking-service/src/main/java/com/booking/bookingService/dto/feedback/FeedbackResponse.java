package com.booking.bookingService.dto.feedback;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FeedbackResponse {
    private UUID id;
    private UUID tripId;
    private Integer rating;
    private String comment;
    private String userEmail; // Returning email to identify reviewer (can be masked)
    private LocalDateTime submittedAt;
}