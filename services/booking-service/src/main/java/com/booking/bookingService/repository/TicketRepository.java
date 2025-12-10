package com.booking.bookingService.repository;

import com.booking.bookingService.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {
    
    // Tìm các vé PENDING đã quá hạn giữ chỗ
    List<Ticket> findByStatusAndLockedUntilBefore(Ticket.TicketStatus status, LocalDateTime now);

    // --- Finder Method for Guest Lookup ---
    Optional<Ticket> findFirstByTicketCode(String ticketCode);
}