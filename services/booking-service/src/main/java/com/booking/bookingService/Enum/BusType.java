package com.booking.bookingService.Enum;

import lombok.Getter;

@Getter
public enum BusType {
    SEATER("Ghế ngồi", false),
    SLEEPER("Giường nằm", true), 
    CABIN("Giường phòng", true); 

    private final String displayName;
    private final boolean isBed;

    BusType(String displayName, boolean isBed) {
        this.displayName = displayName;
        this.isBed = isBed;
    }
}