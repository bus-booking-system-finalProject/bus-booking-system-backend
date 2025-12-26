package com.booking.bookingService.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import com.booking.bookingService.model.Ticket;

@Getter
@AllArgsConstructor
public class TicketSuccessEvent {
    private final Ticket ticket;
}