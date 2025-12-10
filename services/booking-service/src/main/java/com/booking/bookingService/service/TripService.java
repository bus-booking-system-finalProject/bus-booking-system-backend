package com.booking.bookingService.service;

import com.booking.bookingService.dto.*;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import com.booking.bookingService.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final SeatRepository seatRepository;
    private final RouteRepository routeRepository;
    private final TripSeatRepository seatStatusRepository;

    private final RedisLockService redisLockService;

    public Trip createTrip(TripRequest request) {
        validateBusAvailability(request.getBusId(), request.getDepartureTime(), request.getArrivalTime(), null);
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        // Ensure Bus belongs to Operator of Route (optional business rule, but good practice)
        // For now, we assume flexible assignment or strict check:
        // if (!bus.getOperator().getId().equals(route.getOperator().getId())) ...

        Trip trip = Trip.builder()
                .bus(bus)
                .route(route)
                .operator(bus.getOperator()) // Inherit operator from Bus
                .departureTime(request.getDepartureTime())
                .arrivalTime(request.getArrivalTime())
                .price(request.getBasePrice())
                .status(Trip.TripStatus.SCHEDULED)
                .availableSeats(bus.getSeatCapacity())
                .build();
        
        Trip savedTrip = tripRepository.save(trip);

        // Initialize Seat Statuses
        List<Seat> physicalSeats = seatRepository.findByBusId(bus.getId());
        List<TripSeat> statuses = physicalSeats.stream().map(seat -> 
            TripSeat.builder()
                .trip(savedTrip)
                .seat(seat)
                .status(TripSeat.Status.AVAILABLE)
                .build()
        ).collect(Collectors.toList());
        
        seatStatusRepository.saveAll(statuses);

        return trip;
    }

    // --- Update Trip ---
    public Trip updateTrip(UUID tripId, TripRequest request) {
        validateBusAvailability(request.getBusId(), request.getDepartureTime(), request.getArrivalTime(), tripId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        trip.setBus(bus);
        trip.setRoute(route);
        trip.setOperator(bus.getOperator());
        trip.setDepartureTime(request.getDepartureTime());
        trip.setArrivalTime(request.getArrivalTime());
        trip.setPrice(request.getBasePrice());
        
        if (request.getStatus() != null) {
            try {
                trip.setStatus(Trip.TripStatus.valueOf(request.getStatus()));
            } catch (IllegalArgumentException e) {
                // Ignore or throw invalid status exception
            }
        }

        // NOTE: If bus changes, we technically need to regenerate SeatStatuses. 
        // This complexity is omitted for brevity but important in production.

        return tripRepository.save(trip);
    }

    // --- Delete Trip ---
    public void deleteTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        
        // Soft delete (Cancel) or Hard delete?
        // Let's do hard delete for CRUD simplicity, but clean up child records first
        List<TripSeat> statuses = seatStatusRepository.findByTripId(tripId);
        seatStatusRepository.deleteAll(statuses);
        
        tripRepository.delete(trip);
    }

    public Page<TripSearchResponse> searchTrips(TripSearchRequest request) {
        // 1. Setup Sorting & Pagination
        Sort sort = Sort.by(Sort.Direction.ASC, "departureTime"); // Default: Sớm nhất trước

        if (request.getSort() != null && !request.getSort().isEmpty()) {
            switch (request.getSort()) {
                case "earliest":
                    sort = Sort.by(Sort.Direction.ASC, "departureTime");
                    break;
                case "latest":
                    sort = Sort.by(Sort.Direction.DESC, "departureTime");
                    break;
                case "lowest_price":
                    sort = Sort.by(Sort.Direction.ASC, "price");
                    break;
                case "highest_rating":
                    // Lưu ý: Đảm bảo Entity Operator có trường "rating"
                    sort = Sort.by(Sort.Direction.DESC, "operator.rating"); 
                    break;
                default:
                    // Fallback về default nếu gửi sort linh tinh
                    sort = Sort.by(Sort.Direction.ASC, "departureTime");
            }
        }
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getLimit(), sort);

        Specification<Trip> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Joins
            var routeJoin = root.join("route");
            var busJoin = root.join("bus"); 
            var operatorJoin = root.join("operator"); 

            // 1. Origin
            if (request.getOrigin() != null && !request.getOrigin().isEmpty()) {
                String originPattern = "%" + request.getOrigin().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(routeJoin.get("origin")), 
                    originPattern
                ));
            }

            // 2. Destination
            if (request.getDestination() != null && !request.getDestination().isEmpty()) {
                String destPattern = "%" + request.getDestination().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(routeJoin.get("destination")), 
                    destPattern
                ));
            }

            // 3. Date (Specific Day)
            if (request.getDate() != null) {
                LocalDateTime startOfDay = request.getDate().atStartOfDay();
                LocalDateTime endOfDay = request.getDate().atTime(LocalTime.MAX);
                predicates.add(criteriaBuilder.between(root.get("departureTime"), startOfDay, endOfDay));
            }

            // 4. Passenger Capacity (using new availableSeats field)
            if (request.getPassengers() != null && request.getPassengers() > 0) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("availableSeats"), 
                    request.getPassengers()
                ));
            }

            // 5. Bus Type (using new type field on Bus)
            if (request.getBusType() != null && !request.getBusType().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(busJoin.get("type")), 
                    request.getBusType().toLowerCase()
                ));
            }

            // 6. Price Range (using new price field)
            if (request.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("price"), 
                    request.getMinPrice()
                ));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("price"), 
                    request.getMaxPrice()
                ));
            }

            // 7. Operator Filter (using new direct operator relationship)
            if (request.getOperatorId() != null) {
                predicates.add(criteriaBuilder.equal(
                    operatorJoin.get("id"), 
                    request.getOperatorId()
                ));
            }

            // 8. Departure Time Slots
            if (request.getDepartureTime() != null && request.getDate() != null) {
                LocalDateTime baseDate = request.getDate().atStartOfDay();
                String timeSlot = request.getDepartureTime().toLowerCase();
                
                LocalDateTime startTime = null;
                LocalDateTime endTime = null;

                switch (timeSlot) {
                    case "morning": // 06:00 - 11:59
                        startTime = baseDate.withHour(6);
                        endTime = baseDate.withHour(11).withMinute(59);
                        break;
                    case "afternoon": // 12:00 - 17:59
                        startTime = baseDate.withHour(12);
                        endTime = baseDate.withHour(17).withMinute(59);
                        break;
                    case "evening": // 18:00 - 20:59
                        startTime = baseDate.withHour(18);
                        endTime = baseDate.withHour(20).withMinute(59);
                        break;
                    case "night": // 21:00 - 23:59
                        startTime = baseDate.withHour(21);
                        endTime = baseDate.withHour(23).withMinute(59);
                        break;
                }

                if (startTime != null && endTime != null) {
                    predicates.add(criteriaBuilder.between(
                        root.get("departureTime"), 
                        startTime, 
                        endTime
                    ));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return tripRepository.findAll(spec, pageable).map(this::mapToTripResponse);
    }
    
    // --- Get Detail ---
    public TripSearchResponse getTripById(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        return mapToTripResponse(trip);
    }

    // --- Get Seat Map ---
    public SeatMapResponse getSeatMap(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        // 1. Get all physical seats
        List<Seat> physicalSeats = seatRepository.findByBusId(trip.getBus().getId());

        // 2. Get current statuses from DB
        List<TripSeat> seatStatuses = seatStatusRepository.findByTripId(tripId);

        // [START LAZY UPDATE LOGIC]
        List<TripSeat> expiredSeats = new ArrayList<>();
        boolean hasChanges = false;

        for (TripSeat status : seatStatuses) {
            if (status.getStatus() == TripSeat.Status.LOCKED) {
                String redisKey = "lock:seat:" + tripId + ":" + status.getSeat().getSeatCode();

                if (redisLockService.getLockOwner(redisKey) == null) {
                    status.setStatus(TripSeat.Status.AVAILABLE);
                    expiredSeats.add(status);
                    hasChanges = true;
                }
            }
        }

        if (hasChanges) {
            seatStatusRepository.saveAll(expiredSeats);
        }

        Map<UUID, TripSeat.Status> statusMap = seatStatuses.stream()
                .collect(Collectors.toMap(
                        s -> s.getSeat().getId(),
                        TripSeat::getStatus
                ));

        // Calculate dimensions
        int maxRows = physicalSeats.stream().mapToInt(Seat::getGridRow).max().orElse(0);
        int maxCols = physicalSeats.stream().mapToInt(Seat::getGridCol).max().orElse(0);
        int totalDecks = physicalSeats.stream().mapToInt(Seat::getDeckNumber).max().orElse(1);

        // 3. Map to DTOs
        List<SeatMapResponse.SeatDto> seatDtos = physicalSeats.stream().map(seat -> {
            // Lấy trạng thái từ map (lúc này map đã chứa dữ liệu sạch - fresh data)
            String status = statusMap.getOrDefault(seat.getId(), TripSeat.Status.AVAILABLE).name().toLowerCase();
            
            return SeatMapResponse.SeatDto.builder()
                    .seatId(seat.getId().toString())
                    .seatCode(seat.getSeatCode())
                    .deck(seat.getDeckNumber())
                    .price(trip.getPrice()) 
                    .status(status)
                    .row(seat.getGridRow())
                    .col(seat.getGridCol())
                    .build();
        }).collect(Collectors.toList());

        return SeatMapResponse.builder()
                .tripId(tripId)
                .gridRows(maxRows) 
                .gridColumns(maxCols)   
                .totalDecks(totalDecks) 
                .seats(seatDtos)
                .build();
    }

    // --- Helper Mapper ---
    private TripSearchResponse mapToTripResponse(Trip trip) {
        return TripSearchResponse.builder()
                .tripId(trip.getId())
                .status(trip.getStatus().name())
                .route(TripSearchResponse.RouteDto.builder()
                        .origin(trip.getRoute().getOrigin())
                        .destination(trip.getRoute().getDestination())
                        .durationMinutes(trip.getRoute().getEstimatedMinutes())
                        .build())
                .operator(TripSearchResponse.OperatorDto.builder()
                        .name(trip.getOperator().getName())
                        .build())
                .bus(TripSearchResponse.BusDto.builder()
                        .model(trip.getBus().getModel())
                        .type(trip.getBus().getType()) 
                        .build())
                .schedule(TripSearchResponse.ScheduleDto.builder()
                        .departureTime(trip.getDepartureTime())
                        .arrivalTime(trip.getArrivalTime())
                        .build())
                .pricing(TripSearchResponse.PricingDto.builder()
                        .basePrice(trip.getPrice()) 
                        .currency("VND")
                        .build())
                .availability(TripSearchResponse.AvailabilityDto.builder()
                        .totalSeats(trip.getBus().getSeatCapacity())
                        .availableSeats(trip.getAvailableSeats())
                        .build())
                .build();
    }

    private void validateBusAvailability(UUID busId, LocalDateTime start, LocalDateTime end, UUID excludeTripId) {
        List<Trip> conflicts = tripRepository.findConflictingTrips(busId, start, end);
        
        if (excludeTripId != null) {
            conflicts.removeIf(t -> t.getId().equals(excludeTripId));
        }

        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("The selected bus is already booked for this time slot.");
        }
    }
}