package com.booking.bookingService.repository;

import com.booking.bookingService.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StationRepository extends JpaRepository<Station, UUID> {
    List<Station> findByOperatorId(UUID operatorId);
    // Helper to find a specific station during seeding
    Station findByOperatorIdAndName(UUID operatorId, String name);

    List<Station> findByCity(String city);
}