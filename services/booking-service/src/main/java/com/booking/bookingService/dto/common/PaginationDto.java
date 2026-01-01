package com.booking.bookingService.dto.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaginationDto {
    private Integer total;
    private Integer limit;
    private Integer page;
    private Integer totalPages;
}

