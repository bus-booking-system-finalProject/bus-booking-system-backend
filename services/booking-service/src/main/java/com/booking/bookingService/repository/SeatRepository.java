package com.booking.bookingService.repository;

import com.booking.bookingService.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findByBusModelId(UUID busModelId);
}