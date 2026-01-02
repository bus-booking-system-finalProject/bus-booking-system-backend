package com.booking.bookingService.repository;

import com.booking.bookingService.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID>, JpaSpecificationExecutor<Route> {
    // Tìm tuyến đường dựa trên điểm đi và điểm đến
    List<Route> findByOriginAndDestination(String origin, String destination);
    
    // Tìm tất cả tuyến của một nhà xe
    List<Route> findByOperatorId(UUID operatorId);

    List<Route> findAllByOperatorId(UUID operatorId);
}