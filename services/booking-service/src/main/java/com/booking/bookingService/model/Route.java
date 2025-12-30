package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "route")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    private Operator operator;

    private String origin;
    private String destination;
    private int distanceKm;
    private int estimatedMinutes;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("route")
    private List<RouteStop> stops;
}