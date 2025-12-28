package com.booking.bookingService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "route")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    @JsonIgnoreProperties({ "buses", "routes", "trips" })
    private Operator operator;

    private String origin;
    private String destination;
    private int distanceKm;
    private int estimatedMinutes;
}