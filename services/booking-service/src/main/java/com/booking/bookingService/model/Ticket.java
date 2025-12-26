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
    @JoinColumn(name = "pickup_trip_stop_id")
    private TripStop pickupTripStop;

    @ManyToOne
    @JoinColumn(name = "dropoff_trip_stop_id")
    private TripStop dropoffTripStop;

    @OneToOne(mappedBy = "ticket", fetch = FetchType.LAZY)
    private Payment payment;

    public enum TicketStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }

    public String getFullPickupAddress() {
        return pickupTripStop.getFullAddress();
    }

    public String getFullDropoffAddress() {
        return dropoffTripStop.getFullAddress();
    }

    public LocalDateTime getPickupTime() {
        return pickupTripStop.getTime();
    }

    public LocalDateTime getDropoffTime() {
        return dropoffTripStop.getTime();
    }
}