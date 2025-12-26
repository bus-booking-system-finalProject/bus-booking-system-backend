package com.booking.bookingService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripSearchRequest {
    // Required fields
    private String origin;
    private String destination;
    private LocalDate date;

    // Optional fields with defaults
    @Builder.Default
    private Integer passengers = 1;
    
    private List<String> busTypes;       // standard | limousine | sleeper

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime minDepartureTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime maxDepartureTime;
    
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private String sort;
    
    private List<String> operators; // List of Operator Names

    // Pagination
    @Builder.Default
    private Integer page = 1;
    
    @Builder.Default
    private Integer limit = 20;

    
}