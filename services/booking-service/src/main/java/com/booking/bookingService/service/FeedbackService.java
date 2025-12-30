package com.booking.bookingService.service;

import com.booking.bookingService.dto.FeedbackRequest;
import com.booking.bookingService.dto.FeedbackResponse;
import com.booking.bookingService.dto.OperatorReviewsResponse;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Feedback;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Trip;
import com.booking.bookingService.repository.FeedbackRepository;
import com.booking.bookingService.repository.OperatorRepository; // Needed to update rating
import com.booking.bookingService.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final TripRepository tripRepository;
    private final OperatorRepository operatorRepository; // Add this dependency

    @Transactional
    public FeedbackResponse createFeedback(FeedbackRequest request, String userEmail) {
        // 1. Existing Logic...
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        if (feedbackRepository.existsByTripIdAndUserEmail(trip.getId(), userEmail)) {
            throw new IllegalStateException("You have already submitted feedback for this trip.");
        }

        Feedback feedback = Feedback.builder()
                .trip(trip)
                .userEmail(userEmail)
                .rating(request.getRating())
                .comment(request.getComment())
                .submittedAt(LocalDateTime.now())
                .build();

        Feedback saved = feedbackRepository.save(feedback);

        // --- NEW: Update Operator Rating Automatically ---
        updateOperatorRating(trip.getOperator().getId());

        return mapToResponse(saved);
    }

    // --- NEW FEATURE: Get Reviews for Operator ---
    @Transactional(readOnly = true)
    public OperatorReviewsResponse getReviewsForOperator(UUID operatorId, int page, int limit) {
        // 1. Validate Operator
        if (!operatorRepository.existsById(operatorId)) {
             throw new ResourceNotFoundException("Operator not found");
        }

        // 2. Handle Pagination Logic (1-based -> 0-based)
        int dbPage = (page < 1) ? 0 : page - 1; // Convert 1 -> 0
        Pageable pageable = PageRequest.of(dbPage, limit, Sort.by("submittedAt").descending());

        // 3. Fetch Data
        Page<Feedback> feedbackPage = feedbackRepository.findByOperatorId(operatorId, pageable);
        Double avgRating = feedbackRepository.getAverageRatingForOperator(operatorId);

        // 4. Map Reviews
        List<FeedbackResponse> reviewList = feedbackPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        // 5. Build Response with Pagination Metadata
        return OperatorReviewsResponse.builder()
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .totalReviews(feedbackPage.getTotalElements()) // Use Page total for consistency
                .reviews(reviewList)
                .pagination(OperatorReviewsResponse.Pagination.builder()
                        .total(feedbackPage.getTotalElements())
                        .limit(limit)
                        .totalPages(feedbackPage.getTotalPages())
                        .page(dbPage + 1) // Convert back to 1-based for response
                        .build())
                .build();
    }

    // Helper method to recalculate and save Operator rating
    private void updateOperatorRating(UUID operatorId) {
        Double newAverage = feedbackRepository.getAverageRatingForOperator(operatorId);
        if (newAverage != null) {
            Operator operator = operatorRepository.findById(operatorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
            
            // Round to 1 decimal place (e.g., 4.5)
            double roundedRating = Math.round(newAverage * 10.0) / 10.0;
            
            operator.setRating(roundedRating);
            operatorRepository.save(operator);
        }
    }

    private FeedbackResponse mapToResponse(Feedback feedback) {
        return FeedbackResponse.builder()
                .id(feedback.getId())
                .tripId(feedback.getTrip().getId())
                .rating(feedback.getRating())
                .comment(feedback.getComment())
                .userEmail(feedback.getUserEmail())
                .submittedAt(feedback.getSubmittedAt())
                .build();
    }
}