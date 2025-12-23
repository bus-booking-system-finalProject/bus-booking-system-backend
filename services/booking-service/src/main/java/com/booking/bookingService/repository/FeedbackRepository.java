package com.booking.bookingService.repository;

import com.booking.bookingService.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    
    // Check if user (by email) has already submitted feedback for this trip
    boolean existsByTripIdAndUserEmail(UUID tripId, String userEmail);

    // Retrieve all feedback for a specific trip (e.g., to show reviews)
    List<Feedback> findByTripId(UUID tripId);
    
    // 1. Fetch Reviews for an Operator (Paginated)
    // We navigate from Feedback -> Trip -> Operator
    @Query("SELECT f FROM Feedback f WHERE f.trip.operator.id = :operatorId ORDER BY f.submittedAt DESC")
    Page<Feedback> findByOperatorId(@Param("operatorId") UUID operatorId, Pageable pageable);

    // 2. Calculate Average Rating for an Operator
    @Query("SELECT AVG(f.rating) FROM Feedback f WHERE f.trip.operator.id = :operatorId")
    Double getAverageRatingForOperator(@Param("operatorId") UUID operatorId);

    // 3. Count Total Reviews for an Operator
    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.trip.operator.id = :operatorId")
    Long countByOperatorId(@Param("operatorId") UUID operatorId);   
}