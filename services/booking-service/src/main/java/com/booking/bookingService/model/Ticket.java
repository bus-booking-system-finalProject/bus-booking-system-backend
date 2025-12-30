package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ticket") // Đổi tên bảng thành 'ticket'
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String ticketCode; 

    private String userEmail; 
    
    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    private String contactName;
    private String contactEmail;
    private String contactPhone;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private TicketStatus status; // Đổi BookingStatus -> TicketStatus

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime lockedUntil;

    // Bảng phụ lưu ghế
    @ElementCollection
    @CollectionTable(name = "ticket_seats", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "seat_code")
    private List<String> seats; 

    @ManyToOne
    @JoinColumn(name = "pickup_route_stop_id")
    private RouteStop pickupRouteStop;

    @ManyToOne
    @JoinColumn(name = "dropoff_route_stop_id")
    private RouteStop dropoffRouteStop;

    @OneToOne(mappedBy = "ticket", fetch = FetchType.LAZY)
    private Payment payment;

    public enum TicketStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }

    public String getFullPickupAddress() {
        return pickupRouteStop != null ? pickupRouteStop.getFullAddress() : "";
    }

    public String getFullDropoffAddress() {
        return dropoffRouteStop != null ? dropoffRouteStop.getFullAddress() : "";
    }

    public LocalDateTime getPickupTime() {
        if (trip == null || pickupRouteStop == null) return null;
        return trip.getDepartureTime().plusMinutes(pickupRouteStop.getDuration());
    }

    public LocalDateTime getDropoffTime() {
        if (trip == null || dropoffRouteStop == null) return null;
        return trip.getDepartureTime().plusMinutes(dropoffRouteStop.getDuration());
    }
}