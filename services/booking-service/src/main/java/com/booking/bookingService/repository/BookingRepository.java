package com.booking.bookingService.repository;

import com.booking.bookingService.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {
    
    // Tìm các đơn PENDING đã quá giờ lockedUntil
    List<Booking> findByStatusAndLockedUntilBefore(Booking.BookingStatus status, LocalDateTime now);
}