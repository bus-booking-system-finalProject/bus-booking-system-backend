package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "user_email"}) 
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // Requested in requirements
    @Column(name = "user_id")
    private UUID userId;

    // Added for consistency with Ticket.userEmail validation
    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private Integer rating; // 1-5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
}