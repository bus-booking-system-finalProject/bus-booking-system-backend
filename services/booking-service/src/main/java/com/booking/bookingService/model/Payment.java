package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    // Mã đơn hàng số nguyên theo yêu cầu PayOS (dùng để tạo link)
    @Column(unique = true)
    private Long orderCode; 

    private BigDecimal amount;
    private String status; // PENDING, PAID, CANCELLED

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}