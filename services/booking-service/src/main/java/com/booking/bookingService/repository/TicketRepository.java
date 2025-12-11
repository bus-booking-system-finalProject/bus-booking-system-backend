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
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    
    // Tìm các vé PENDING đã quá hạn giữ chỗ
    List<Ticket> findByStatusAndLockedUntilBefore(Ticket.TicketStatus status, LocalDateTime now);

    @Override
    @EntityGraph(attributePaths = {"trip", "trip.route", "trip.operator"})
    Page<Ticket> findAll(@Nullable Specification<Ticket> spec, Pageable pageable);
}