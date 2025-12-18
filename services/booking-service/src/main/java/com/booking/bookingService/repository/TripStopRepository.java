package com.booking.bookingService.repository;

import com.booking.bookingService.model.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
    
    // Fetch all stops (Pickup & Dropoff) for a specific trip
    List<TripStop> findByTripId(UUID tripId);

    // Optional: Fetch only Pickups or only Dropoffs for a trip
    // Useful if you want to show them in separate dropdowns
    List<TripStop> findByTripIdAndType(UUID tripId, TripStop.StopType type);
}