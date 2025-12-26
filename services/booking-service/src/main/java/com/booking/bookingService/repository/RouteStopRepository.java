package com.booking.bookingService.repository;

import com.booking.bookingService.model.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {
    // Fetch stops ordered by sequence for correct timeline generation
    List<RouteStop> findByRouteIdOrderByOrderIndexAsc(UUID routeId);
}