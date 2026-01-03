package com.booking.bookingService.repository;

import com.booking.bookingService.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID>, JpaSpecificationExecutor<Trip> {

    // FIX: Use Native Query for reliable date arithmetic with Intervals
    @Query(value = """
        SELECT t.* FROM trip t
        INNER JOIN route r ON t.route_id = r.id
        WHERE t.bus_id = :busId
        AND t.status != 'CANCELLED'
        AND t.departure_time < :endTime
        AND (:startTime < (t.departure_time + (r.estimated_minutes * INTERVAL '1 minute')))
    """, nativeQuery = true)
    List<Trip> findConflictingTrips(
            @Param("busId") UUID busId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    // Concurrency Safe Decrement
    @Modifying
    @Query("UPDATE Trip t SET t.availableSeats = t.availableSeats - :amount " +
           "WHERE t.id = :tripId AND t.availableSeats >= :amount")
    int decrementAvailableSeats(@Param("tripId") UUID tripId, @Param("amount") int amount);

    // Concurrency Safe Increment
    @Modifying
    @Query("UPDATE Trip t SET t.availableSeats = t.availableSeats + :amount WHERE t.id = :tripId")
    void incrementAvailableSeats(@Param("tripId") UUID tripId, @Param("amount") int amount);

    List<Trip> findAllByOperatorId(UUID operatorId);

    Boolean existsByRouteIdAndDepartureTime(UUID routeId, LocalDateTime departureTime);

    Boolean existsByRouteId(UUID routeId);
}