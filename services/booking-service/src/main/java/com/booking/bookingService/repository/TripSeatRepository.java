package com.booking.bookingService.repository;

import com.booking.bookingService.model.TripSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TripSeatRepository extends JpaRepository<TripSeat, UUID> {
    List<TripSeat> findByTripId(UUID tripId);
    List<TripSeat> findByTripIdAndSeat_SeatCodeIn(UUID tripId, List<String> seatCodes);
}