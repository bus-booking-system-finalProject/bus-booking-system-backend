package com.booking.bookingService.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class CashPaymentRequest {
    @NotNull(message = "Ticket ID is required")
    private UUID ticketId;
}
