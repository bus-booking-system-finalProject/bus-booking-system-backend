package com.booking.bookingService.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PassengerRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;
    
    private String documentId; // CCCD/CMND
    private String phone;
    
    @NotBlank(message = "Seat code is required")
    private String seatCode;
}