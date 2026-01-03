package com.booking.bookingService.repository;

import com.booking.bookingService.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Fulltext search for routes by name, origin, or destination
    @Query("SELECT r FROM Route r WHERE " +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.origin) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.destination) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Route> searchByKeyword(@Param("keyword") String keyword);

    // Fulltext search for routes with operator filter
    @Query("SELECT r FROM Route r WHERE r.operator.id = :operatorId AND (" +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.origin) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.destination) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Route> searchByKeywordAndOperatorId(@Param("keyword") String keyword, @Param("operatorId") UUID operatorId);

    // Search active routes only
    @Query("SELECT r FROM Route r WHERE r.isActive = true AND (" +
            "LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.origin) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.destination) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Route> searchActiveByKeyword(@Param("keyword") String keyword);

    // Search routes by origin and destination with partial matching
    @Query("SELECT r FROM Route r WHERE r.isActive = true AND " +
            "LOWER(r.origin) LIKE LOWER(CONCAT('%', :origin, '%')) AND " +
            "LOWER(r.destination) LIKE LOWER(CONCAT('%', :destination, '%'))")
    List<Route> searchByOriginAndDestination(@Param("origin") String origin, @Param("destination") String destination);
}