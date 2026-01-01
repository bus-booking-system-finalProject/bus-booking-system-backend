package com.booking.bookingService.dto.station;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class StationRequest {
    private UUID operatorId;

    @NotBlank(message = "Station name is required")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    private String ward;

    @NotBlank(message = "City is required")
    private String city;
}