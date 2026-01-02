package com.booking.bookingService.repository;

import com.booking.bookingService.model.Bus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BusRepository extends JpaRepository<Bus, UUID> {
    // Tìm tất cả xe của một nhà xe cụ thể
    List<Bus> findByOperatorId(UUID operatorId);
    
    // Tìm xe theo biển số
    // Optional<Bus> findByPlateNumber(String plateNumber);

    List<Bus> findAllByOperatorId(UUID operatorId);
}