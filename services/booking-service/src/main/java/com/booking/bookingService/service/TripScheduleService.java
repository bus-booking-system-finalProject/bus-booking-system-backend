package com.booking.bookingService.service;

import com.booking.bookingService.dto.schedule.TripScheduleRequest;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripScheduleService {

    private final TripScheduleRepository scheduleRepository;
    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final BusModelRepository busModelRepository; // Changed from BusTypeRepository
    private final TripSeatRepository seatStatusRepository;
    private final SeatRepository seatRepository;
    private final OperatorRepository operatorRepository;

    @Transactional
    public TripSchedule createSchedule(TripScheduleRequest request, UUID operatorId) {
        Operator operator = operatorRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        // Use BusModel instead of BusType
        BusModel busModel = busModelRepository.findById(request.getBusModelId()) // request.getBusTypeId() renamed to getBusModelId() ideally, or cast it
                .orElseThrow(() -> new ResourceNotFoundException("BusModel not found"));

        // Ownership checks...
        if (!route.getOperator().getId().equals(operatorId)) throw new RuntimeException("Not your route");
        if (!busModel.getOperator().getId().equals(operatorId)) throw new RuntimeException("Not your bus model");

        TripSchedule schedule = TripSchedule.builder()
                .operator(operator)
                .route(route)
                .busModel(busModel) // Set Model
                .departureTime(request.getDepartureTime())
                .daysOfWeek(request.getDaysOfWeek())
                .active(request.isActive())
                .build();

        TripSchedule saved = scheduleRepository.save(schedule);
        
        // Trigger generation immediately for the upcoming days
        generateTripsForSchedule(saved, LocalDate.now(), LocalDate.now().plusMonths(2));
        
        return saved;
    }
    
    public List<TripSchedule> getMySchedules(UUID operatorId) {
        return scheduleRepository.findAllByOperatorId(operatorId);
    }

    /**
     * CRON JOB: Runs every day at 02:00 AM
     * Generates trips for the next 60 days based on active schedules.
     */
    @Scheduled(cron = "0 0 2 * * ?") 
    @Transactional
    public void generateDailyTrips() {
        log.info("Starting Auto-Trip Generation...");
        List<TripSchedule> activeSchedules = scheduleRepository.findByActiveTrue();
        
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(2);

        for (TripSchedule schedule : activeSchedules) {
            generateTripsForSchedule(schedule, start, end);
        }
        log.info("Auto-Trip Generation Complete.");
    }

    private void generateTripsForSchedule(TripSchedule schedule, LocalDate start, LocalDate end) {
        LocalDate current = start;

        while (!current.isAfter(end)) {
            // 1. Check if today matches the schedule (e.g., is MONDAY?)
            if (schedule.getDaysOfWeek().contains(current.getDayOfWeek())) {
                LocalDateTime departure = LocalDateTime.of(current, schedule.getDepartureTime());

                // 2. Check if Trip already exists (Avoid duplicates)
                boolean exists = tripRepository.existsByRouteIdAndDepartureTime(
                        schedule.getRoute().getId(), departure);
                
                if (!exists) {
                    createTripFromSchedule(schedule, departure);
                }
            }
            current = current.plusDays(1);
        }
    }

    private void createTripFromSchedule(TripSchedule schedule, LocalDateTime departure) {
        Trip trip = Trip.builder()
                .operator(schedule.getOperator())
                .route(schedule.getRoute())
                .busModel(schedule.getBusModel()) // Set Model template
                .bus(null) // BUS IS NULL INITIALLY
                .departureTime(departure)
                .status(Trip.TripStatus.SCHEDULED)
                .availableSeats(schedule.getBusModel().getSeatCapacity())
                .originalPrice(java.math.BigDecimal.ZERO)
                .build();

        tripRepository.save(trip);
        
        // Initialize Seats based on BUS MODEL
        List<Seat> templateSeats = seatRepository.findByBusModelId(schedule.getBusModel().getId());
        List<TripSeat> tripSeats = templateSeats.stream().map(seat -> TripSeat.builder()
                .trip(trip)
                .seat(seat)
                .status(TripSeat.Status.AVAILABLE)
                .build()).collect(Collectors.toList());
        
        seatStatusRepository.saveAll(tripSeats);
    }
}