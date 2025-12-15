package com.booking.bookingService.repository;

import com.booking.bookingService.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderCode(Long orderCode);
    Optional<Payment> findByTicketId(UUID ticketId);
}