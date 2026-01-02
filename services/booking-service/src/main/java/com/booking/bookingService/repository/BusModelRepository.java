package com.booking.bookingService.repository;

import com.booking.bookingService.model.BusModel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BusModelRepository extends JpaRepository<BusModel, UUID> {
    // Tìm tất cả xe của một nhà xe cụ thể
    List<BusModel> findByOperatorId(UUID operatorId);
    
    // Tìm xe theo biển số
    // Optional<Bus> findByPlateNumber(String plateNumber);

    List<BusModel> findAllByOperatorId(UUID operatorId);
}