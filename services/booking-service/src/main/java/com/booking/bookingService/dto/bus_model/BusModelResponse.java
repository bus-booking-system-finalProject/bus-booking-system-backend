package com.booking.bookingService.dto.bus_model;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class BusModelResponse {
    private UUID id;

    private String name;
    private String typeDisplay;
    private String type;
    private Boolean isLimousine;
    private Boolean hasWC;

    private int seatCapacity;
}
