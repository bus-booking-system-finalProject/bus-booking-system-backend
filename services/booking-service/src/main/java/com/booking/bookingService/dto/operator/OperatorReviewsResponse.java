package com.booking.bookingService.dto.operator;

import lombok.Builder;
import lombok.Data;
import java.util.List;

import com.booking.bookingService.dto.feedback.FeedbackResponse;

@Data @Builder
public class OperatorReviewsResponse {
    private Double averageRating;
    private Long totalReviews;
    private List<FeedbackResponse> reviews;
    private Pagination pagination; // New Field

    @Data @Builder
    public static class Pagination {
        private long total;      // Total items
        private int limit;       // Items per page
        private int totalPages;  // Total pages
        private int page;        // Current page (1-based)
    }
}