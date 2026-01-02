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
    
    @Query("SELECT t FROM Trip t " +
           "WHERE t.bus.id = :busId " +
           "AND t.status != 'CANCELLED' " +
           "AND ((t.departureTime < :endTime) AND " +
           "(CAST(t.departureTime AS timestamp) + (t.route.estimatedMinutes MINUTE) > :startTime))")
    List<Trip> findConflictingTrips(
            @Param("busId") UUID busId, 
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime
    );

    @Modifying
    @Query("UPDATE Trip t SET t.availableSeats = t.availableSeats - :amount " +
           "WHERE t.id = :tripId AND t.availableSeats >= :amount")
    int decrementAvailableSeats(@Param("tripId") UUID tripId, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Trip t SET t.availableSeats = t.availableSeats + :amount WHERE t.id = :tripId")
    void incrementAvailableSeats(@Param("tripId") UUID tripId, @Param("amount") int amount);

    List<Trip> findAllByOperatorId(UUID operatorId);

    Boolean existsByRouteIdAndDepartureTime(UUID routeId, LocalDateTime departureTime);

    Boolean existsByRouteId(UUID routeId);
}