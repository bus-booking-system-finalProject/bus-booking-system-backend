package com.booking.bookingService.dto.station;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class StationResponse {
    private UUID id;
    private String name;
    private String address;
    private String ward;
    private String city;
    private UUID operatorId;
    private String operatorName;
}