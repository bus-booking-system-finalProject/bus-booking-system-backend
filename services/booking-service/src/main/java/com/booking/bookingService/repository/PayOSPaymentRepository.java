package com.booking.bookingService.repository;

import com.booking.bookingService.model.PayOSPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayOSPaymentRepository extends JpaRepository<PayOSPayment, UUID> {
    // This works now because PayOSPayment has the orderCode field
    Optional<PayOSPayment> findByOrderCode(Long orderCode);
}