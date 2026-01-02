package com.booking.bookingService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Direct link to Operator allows for easier querying and checking ownership
    @ManyToOne
    @JoinColumn(name = "operator_id")
    @JsonIgnoreProperties({ "buses", "routes", "trips" })
    private Operator operator;

    @ManyToOne
    @JoinColumn(name = "route_id")
    @JsonIgnoreProperties({ "stops", "trips" })
    private Route route;

    @ManyToOne
    @JoinColumn(name = "bus_id", nullable = true)
    @JsonIgnoreProperties({ "operator", "trips", "seats" })
    private Bus bus;

    @ManyToOne
    @JoinColumn(name = "bus_model_id", nullable = false)
    private BusModel busModel;

    private LocalDateTime departureTime;

    private BigDecimal originalPrice;

    @Builder.Default
    private BigDecimal discountPrice = BigDecimal.ONE.negate();

    // Cached count of available seats for performant searching
    // This should be updated transactionally whenever a booking occurs
    private int availableSeats;

    @Enumerated(EnumType.STRING)
    private TripStatus status;

    public enum TripStatus {
        SCHEDULED, DELAYED, CANCELLED, COMPLETED
    }

    public LocalDateTime getArrivalTime() {
        if (departureTime != null && route != null) {
            return departureTime.plusMinutes(route.getEstimatedMinutes());
        }
        return null;
    }

    public BigDecimal getPrice() {
        BigDecimal price = originalPrice;
        if (discountPrice.compareTo(BigDecimal.ZERO) > 0) {
            price = discountPrice;
        }
        return price;
    }
}