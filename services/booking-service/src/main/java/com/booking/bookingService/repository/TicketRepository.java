package com.booking.bookingService.repository;

import com.booking.bookingService.model.Ticket;
import org.springframework.data.domain.Page;          
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    
    // Tìm các vé PENDING đã quá hạn giữ chỗ
    List<Ticket> findByStatusAndLockedUntilBefore(Ticket.TicketStatus status, LocalDateTime now);

    @Override
    @EntityGraph(attributePaths = {"trip", "trip.route", "trip.operator"})
    Page<Ticket> findAll(@Nullable Specification<Ticket> spec, Pageable pageable);
    // --- Finder Method for Guest Lookup ---
    Optional<Ticket> findFirstByTicketCode(String ticketCode);

    // --- ANALYTICS QUERY (Phase 1) ---
    // Note: We use Native Query for reliable Date grouping. 
    // Return List<Object[]>: [Date, BigDecimal, Long]
    @Query(value = """
        SELECT 
            CAST(created_at AS DATE) as date, 
            COALESCE(SUM(total_amount), 0) as revenue, 
            COUNT(id) as count 
        FROM ticket 
        WHERE status = :status 
        AND created_at BETWEEN :startDate AND :endDate 
        GROUP BY CAST(created_at AS DATE) 
        ORDER BY date ASC
    """, nativeQuery = true)
    List<Object[]> findDailyTrends(
        @Param("status") String status, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );

    // --- ANALYTICS QUERY (Phase 2: Popular Routes) ---
    // Return List<Object[]>: [Origin, Destination, Count, Revenue]
    @Query(value = """
        SELECT 
            r.origin, 
            r.destination, 
            COUNT(t.id) as total_bookings, 
            COALESCE(SUM(t.total_amount), 0) as total_revenue
        FROM ticket t
        JOIN trip tr ON t.trip_id = tr.id
        JOIN route r ON tr.route_id = r.id
        WHERE t.status = :status 
        AND t.created_at BETWEEN :startDate AND :endDate
        GROUP BY r.id, r.origin, r.destination
        ORDER BY total_bookings DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findPopularRoutes(
        @Param("status") String status, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate,
        @Param("limit") int limit
    );

    // --- ANALYTICS QUERY (Phase 4: Summary) ---
    // Returns a single object array: [Total, Confirmed, Pending, Cancelled, Revenue]
    @Query(value = """
        SELECT 
            COUNT(id) as total_tickets,
            SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed_count,
            SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending_count,
            SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelled_count,
            COALESCE(SUM(CASE WHEN status = 'CONFIRMED' THEN total_amount ELSE 0 END), 0) as total_revenue
        FROM ticket
        WHERE created_at BETWEEN :startDate AND :endDate
    """, nativeQuery = true)
    List<Object[]> getSummaryStats(
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
}