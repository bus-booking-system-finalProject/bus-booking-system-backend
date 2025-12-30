package com.booking.bookingService.service;

import com.booking.bookingService.dto.FeedbackRequest;
import com.booking.bookingService.dto.FeedbackResponse;
import com.booking.bookingService.dto.OperatorReviewsResponse;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Feedback;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Trip;
import com.booking.bookingService.repository.FeedbackRepository;
import com.booking.bookingService.repository.OperatorRepository;
import com.booking.bookingService.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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
    private final OperatorRepository operatorRepository;

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

        // --- CHANGED: Update BOTH Rating and Count ---
        updateOperatorStats(trip.getOperator().getId());

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OperatorReviewsResponse getReviewsForOperator(UUID operatorId, int page, int limit) {
        // 1. Validate Operator
        if (!operatorRepository.existsById(operatorId)) {
             throw new ResourceNotFoundException("Operator not found");
        }

        // 2. Handle Pagination Logic (1-based -> 0-based)
        int dbPage = (page < 1) ? 0 : page - 1; 
        Pageable pageable = PageRequest.of(dbPage, limit, Sort.by("submittedAt").descending());

        // 3. Fetch Data
        Page<Feedback> feedbackPage = feedbackRepository.findByOperatorId(operatorId, pageable);
        Double avgRating = feedbackRepository.getAverageRatingForOperator(operatorId);
        
        // Use the Page's total elements for consistency
        long totalElements = feedbackPage.getTotalElements();

        // 4. Map Reviews
        List<FeedbackResponse> reviewList = feedbackPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        // 5. Build Response
        return OperatorReviewsResponse.builder()
                .averageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0)
                .totalReviews(totalElements)
                .reviews(reviewList)
                .pagination(OperatorReviewsResponse.Pagination.builder()
                        .total(totalElements)
                        .limit(limit)
                        .totalPages(feedbackPage.getTotalPages())
                        .page(dbPage + 1)
                        .build())
                .build();
    }

    // --- CORRECTION HERE ---
    // Renamed to updateOperatorStats and added setTotalReviews logic
    private void updateOperatorStats(UUID operatorId) {
        Double newAverage = feedbackRepository.getAverageRatingForOperator(operatorId);
        Long totalCount = feedbackRepository.countByOperatorId(operatorId); // Count reviews

        Operator operator = operatorRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
        
        // Update Rating
        if (newAverage != null) {
            double roundedRating = Math.round(newAverage * 10.0) / 10.0;
            operator.setRating(roundedRating);
        } else {
            operator.setRating(0.0);
        }

        // Update Total Reviews (Missing in your code)
        operator.setTotalReviews(totalCount != null ? totalCount.intValue() : 0);

        operatorRepository.save(operator);
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

    // --- NEW FEATURE: Get My Review ---
    @Transactional(readOnly = true)
    public FeedbackResponse getMyFeedbackForTrip(UUID tripId, String userEmail) {
        return feedbackRepository.findByTripIdAndUserEmail(tripId, userEmail)
                .map(this::mapToResponse)
                .orElse(null); // Return null if not found (Controller will handle this)
    }
}