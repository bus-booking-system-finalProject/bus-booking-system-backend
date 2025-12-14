// src/main/java/com/booking/bookingService/repository/SearchLogRepository.java
package com.booking.bookingService.repository;

import com.booking.bookingService.model.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, UUID> {

    @Query("SELECT COUNT(s) FROM SearchLog s WHERE s.searchedAt BETWEEN :startDate AND :endDate")
    long countSearchesInRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}