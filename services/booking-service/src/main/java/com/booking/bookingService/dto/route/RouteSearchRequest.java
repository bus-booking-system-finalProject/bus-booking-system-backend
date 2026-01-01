package com.booking.bookingService.dto.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteSearchRequest {
    private String name;
    private String origin;
    private String destination;
    private String operator;
    
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer limit = 20;
}