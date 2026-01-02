package com.booking.bookingService.controller;

import com.booking.bookingService.annotation.CurrentOperator;
import com.booking.bookingService.dto.ApiResponse;
import com.booking.bookingService.dto.schedule.TripScheduleRequest;
import com.booking.bookingService.service.TripScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class TripScheduleController {
    
    private final TripScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<?> createSchedule(
            @Valid @RequestBody TripScheduleRequest request, 
            @CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.createSchedule(request, operatorId), 
                "Schedule created and trips generated"
        ));
    }

    @GetMapping
    public ResponseEntity<?> getMySchedules(@CurrentOperator UUID operatorId) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.getMySchedules(operatorId), 
                "Schedules retrieved"
        ));
    }
}