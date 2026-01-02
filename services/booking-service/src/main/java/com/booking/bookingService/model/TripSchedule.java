package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "trip_schedule")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TripSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    private Operator operator;

    @ManyToOne
    @JoinColumn(name = "route_id")
    private Route route;

    @ManyToOne
    @JoinColumn(name = "bus_model_id")
    private BusModel busModel;

    private LocalTime departureTime; // e.g., 08:00

    // e.g., "MONDAY,WEDNESDAY,FRIDAY" - Stored as simple CSV or ElementCollection
    @ElementCollection
    @CollectionTable(name = "trip_schedule_days", joinColumns = @JoinColumn(name = "schedule_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek;

    @Builder.Default
    private boolean active = true;
}