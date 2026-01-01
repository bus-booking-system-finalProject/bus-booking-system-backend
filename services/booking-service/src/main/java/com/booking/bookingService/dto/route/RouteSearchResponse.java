package com.booking.bookingService.dto.route;

import com.booking.bookingService.dto.common.PaginationDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteSearchResponse {
    private List<RouteResponse> routes;

    private PaginationDto pagination;
}