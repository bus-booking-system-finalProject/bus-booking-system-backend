package com.booking.bookingService.dto.bus;

import java.util.UUID;
import com.booking.bookingService.dto.bus_model.BusModelDetailsResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BusDetailsResponse {
    private UUID id;
    private BusModelDetailsResponse model;
    private String plateNumber;
    private Boolean isActive;
}
