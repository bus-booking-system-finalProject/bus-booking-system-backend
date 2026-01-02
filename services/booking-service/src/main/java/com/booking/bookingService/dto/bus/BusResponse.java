package com.booking.bookingService.dto.bus;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

import com.booking.bookingService.dto.bus_model.BusModelResponse;

@Data
@Builder
public class BusResponse {
    private UUID id;
    private String plateNumber;
    private BusModelResponse model;
    private Boolean isActive;
}