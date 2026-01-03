package com.booking.bookingService.repository;

import com.booking.bookingService.model.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface StationRepository extends JpaRepository<Station, UUID> {
    List<Station> findByOperatorId(UUID operatorId);

    // Helper to find a specific station during seeding
    Station findByOperatorIdAndName(UUID operatorId, String name);

    List<Station> findByCity(String city);

    List<Station> findAllByOperatorId(UUID operatorId);

    // Fulltext search for stations by name, address, ward, or city
    @Query("SELECT s FROM Station s WHERE " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.ward) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.city) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Station> searchByKeyword(@Param("keyword") String keyword);

    // Fulltext search for stations with operator filter
    @Query("SELECT s FROM Station s WHERE s.operator.id = :operatorId AND (" +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.ward) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.city) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Station> searchByKeywordAndOperatorId(@Param("keyword") String keyword, @Param("operatorId") UUID operatorId);

    // Search stations by city with partial matching
    @Query("SELECT s FROM Station s WHERE LOWER(s.city) LIKE LOWER(CONCAT('%', :city, '%'))")
    List<Station> searchByCity(@Param("city") String city);
}