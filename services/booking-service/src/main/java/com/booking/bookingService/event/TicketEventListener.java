package com.booking.bookingService.event;

import com.booking.bookingService.model.Ticket;
import com.booking.bookingService.repository.TicketRepository;
import com.booking.bookingService.service.EmailService;
import com.booking.bookingService.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketEventListener {

    private final TicketRepository ticketRepository;
    private final EmailService emailService;

    /**
     * @Async: Runs this method in a separate thread.
     * @TransactionalEventListener: Only fires AFTER the transaction commits successfully.
     * Propagation.REQUIRES_NEW: MUST be used here to open a fresh DB session for lazy loading.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void handleTicketSuccess(TicketSuccessEvent event) {
        log.info("Starting background processing for Ticket: {}", event.getTicketId());

        try {
            Ticket ticket = ticketRepository.findById(event.getTicketId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

            // Send Email (Heavy Network task)
            emailService.sendTicketEmail(
                    ticket.getContactEmail(),
                    "Ticket Confirmation - " + ticket.getTicketCode(),
                    "<h1>Ticket Confirmed!</h1><p>Thank you for Ticket with Vexesieure. Your e-ticket is attached.</p>"
            );

            log.info("Successfully sent e-ticket for Ticket: {}", event.getTicketId());

        } catch (Exception e) {
            // In a real system, you might send this to a "Dead Letter Queue" or retry table
            log.error("Failed to process Ticket event for ID: " + event.getTicketId(), e);
        }
    }
}