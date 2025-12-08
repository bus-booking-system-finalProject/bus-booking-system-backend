package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "trip_seat")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TripSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Enumerated(EnumType.STRING)
    private Status status; // AVAILABLE, LOCKED, BOOKED

    public enum Status { AVAILABLE, LOCKED, BOOKED }
}