package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_stop")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TripStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip; // Link back to the Trip

    @Enumerated(EnumType.STRING)
    private StopType type; // PICKUP or DROPOFF

    private int orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private Station station;
    
    private LocalDateTime time; // Specific time for this stop

    public enum StopType { PICKUP, DROPOFF }

    // Helper to display full address in UI or Email
    public String getFullAddress() {
        return String.format("%s, %s, %s", station.getAddress(), station.getWard(), station.getCity());
    }
}