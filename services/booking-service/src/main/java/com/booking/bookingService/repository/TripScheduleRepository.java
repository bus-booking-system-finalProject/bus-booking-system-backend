package com.booking.bookingService.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.booking.bookingService.model.TripSchedule;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripScheduleRepository extends JpaRepository<TripSchedule, UUID> {
    List<TripSchedule> findAllByOperatorId(UUID operatorId);

    List<TripSchedule> findByActiveTrue();
}