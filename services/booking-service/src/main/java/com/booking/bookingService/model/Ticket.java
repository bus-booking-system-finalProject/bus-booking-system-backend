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

    // --- SNAPSHOT of Selected Pickup ---
    private String pickupLocation; // Name of the stop
    @Column(name = "pickup_street")
    private String pickupAddress;
    private String pickupWard;
    private String pickupCity;
    private LocalDateTime pickupTime;

    // --- SNAPSHOT of Selected Dropoff ---
    private String dropoffLocation;
    @Column(name = "dropoff_street")
    private String dropoffAddress;
    private String dropoffWard;
    private String dropoffCity;
    private LocalDateTime dropoffTime;

    @OneToOne(mappedBy = "ticket", fetch = FetchType.LAZY)
    private Payment payment;

    public enum TicketStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }

    // Helpers for Email Service
    public String getFullPickupAddress() {
        return String.format("%s, %s, %s", pickupAddress, pickupWard, pickupCity);
    }

    public String getFullDropoffAddress() {
        return String.format("%s, %s, %s", dropoffAddress, dropoffWard, dropoffCity);
    }
}