package com.booking.bookingService.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TicketSuccessEvent {
    private final UUID ticketId;
    private final String userEmail; // Backup email if not in booking entity
}