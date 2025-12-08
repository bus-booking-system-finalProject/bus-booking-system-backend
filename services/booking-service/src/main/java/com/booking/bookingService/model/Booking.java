package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true)
    private String bookingReference;

    private String userId;
    
    @ManyToOne
    @JoinColumn(name = "trip_id")
    private Trip trip;

    private String contactEmail;
    private String contactPhone;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime lockedUntil;

    // THAY ĐỔI: Lưu danh sách ghế trực tiếp
    @ElementCollection
    @CollectionTable(name = "booking_seats", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "seat_code")
    private List<String> seats; 

    public enum BookingStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }
}