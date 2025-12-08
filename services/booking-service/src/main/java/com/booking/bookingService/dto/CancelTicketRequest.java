package com.booking.bookingService.dto;

import lombok.Data;

@Data
public class CancelTicketRequest {
    private String reason;
    private boolean requestRefund;
}
