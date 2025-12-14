// src/main/java/com/booking/bookingService/service/SearchLogService.java
package com.booking.bookingService.service;

import com.booking.bookingService.dto.TripSearchRequest;
import com.booking.bookingService.model.SearchLog;
import com.booking.bookingService.repository.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;

    @Async // Run in a separate thread
    public void logSearchEvent(TripSearchRequest request) {
        // Only log if we have the basics (prevent logging empty page loads if any)
        if (request.getOrigin() != null && request.getDestination() != null) {
            SearchLog log = SearchLog.builder()
                    .origin(request.getOrigin())
                    .destination(request.getDestination())
                    .travelDate(request.getDate())
                    .searchedAt(LocalDateTime.now())
                    .build();
            
            searchLogRepository.save(log);
        }
    }
}