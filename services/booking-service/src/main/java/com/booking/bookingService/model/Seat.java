package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "seat")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "bus_model_id")
    @JsonIgnore
    private BusModel busModel;

    private String seatCode; // e.g., A1, B2
    private int gridRow;    // Vertical position (1, 2, 3...)
    private int gridCol;    // Horizontal position (1, 2, 3, 4, 5...)
    private int deckNumber; // 1 or 2

}