package com.booking.bookingService.dto.ticket;

public class GuestLookupRequest {
    private String ticketCode;
    private String verificationValue;

    // Constructors
    public GuestLookupRequest() {}

    public GuestLookupRequest(String ticketCode, String verificationValue) {
        this.ticketCode = ticketCode;
        this.verificationValue = verificationValue;
    }

    // Getters and Setters
    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public String getVerificationValue() {
        return verificationValue;
    }

    public void setVerificationValue(String verificationValue) {
        this.verificationValue = verificationValue;
    }
}