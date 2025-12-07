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
    private String bookingReference; // Mã vé business code

    private String userId; // Lưu email hoặc User ID từ token
    
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
    
    // Hết hạn giữ chỗ (cho trạng thái PENDING)
    private LocalDateTime lockedUntil;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<Passenger> passengers;

    public enum BookingStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }
}