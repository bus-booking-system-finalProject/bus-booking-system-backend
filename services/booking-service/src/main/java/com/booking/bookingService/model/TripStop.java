package com.booking.bookingService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.booking.bookingService.Enum.StopType;

@Entity
@Table(name = "trip_stop")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class TripStop {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    @JsonIgnoreProperties({ "stops", "bus", "route", "operator" })
    private Trip trip; // Link back to the Trip

    @Enumerated(EnumType.STRING)
    private StopType type; // PICKUP or DROPOFF

    private int orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    @JsonIgnoreProperties({ "routes" })
    private Station station;

    private LocalDateTime time;

    // Helper to display full address in UI or Email
    public String getFullAddress() {
        return String.format("%s, %s, %s", station.getAddress(), station.getWard(), station.getCity());
    }
}