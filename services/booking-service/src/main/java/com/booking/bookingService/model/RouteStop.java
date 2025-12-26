package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

import com.booking.bookingService.Enum.StopType;

@Entity
@Table(name = "route_stop")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private Station station;

    @Enumerated(EnumType.STRING)
    private StopType type; // PICKUP or DROPOFF

    private int orderIndex; // 0, 1, 2... sequence
    
    // How many minutes after Departure Time does the bus arrive here?
    // e.g., 0 for start point, 30 for first pickup, 360 (6 hours) for destination
    private int timeOffsetMinutes;
}