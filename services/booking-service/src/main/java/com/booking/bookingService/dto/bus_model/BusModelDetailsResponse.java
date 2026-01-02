package com.booking.bookingService.dto.bus_model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BusModelDetailsResponse {
    private BusModelResponse details;
    private int totalDecks;
    private int gridRows;
    private int gridColumns;
    private List<SeatDto> seats;

}