package com.booking.bookingService.repository;

import com.booking.bookingService.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SeatStatusRepository extends JpaRepository<SeatStatus, UUID> {
    List<SeatStatus> findByTripId(UUID tripId);
    List<SeatStatus> findByTripIdAndSeat_SeatCodeIn(UUID tripId, List<String> seatCodes);
}