package com.booking.bookingService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bus")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Bus {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "operator_id")
    @JsonIgnoreProperties({ "buses", "routes", "trips" })
    private Operator operator;

    private String plateNumber;
    private String model;

    // Added type to support filtering (Standard, Limousine, Sleeper)
    // You could also use an Enum here
    private String type;

    private int seatCapacity;

    @OneToMany(mappedBy = "bus")
    @JsonIgnoreProperties({ "bus" })
    private List<Seat> seats;
}