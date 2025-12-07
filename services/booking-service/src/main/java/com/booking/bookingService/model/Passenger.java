package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "passenger")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Passenger {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    private String fullName;
    private String documentId;
    private String phone;
    
    // Ghế mà hành khách này ngồi
    private String seatCode; 
}