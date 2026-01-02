package com.booking.bookingService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.*;
import lombok.*;
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

    @ManyToOne
    @JoinColumn(name = "bus_model_id")
    @JsonIgnoreProperties("buses")
    private BusModel model;

    private String plateNumber;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    @JsonProperty("isActive")
    private boolean isActive = true;
}