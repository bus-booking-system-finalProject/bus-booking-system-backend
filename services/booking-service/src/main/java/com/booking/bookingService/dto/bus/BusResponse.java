package com.booking.bookingService.dto.bus;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class BusResponse {
    private UUID id;
    private UUID operatorId;
    private String operatorName;
    private String plateNumber;
    private String model;
    private String type;
    private int seatCapacity;
}