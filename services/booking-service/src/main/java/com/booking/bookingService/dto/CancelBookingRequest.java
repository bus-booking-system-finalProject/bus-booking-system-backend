package com.booking.bookingService.dto;

import lombok.Data;

@Data
public class CancelBookingRequest {
    private String reason;
    private boolean requestRefund;
}