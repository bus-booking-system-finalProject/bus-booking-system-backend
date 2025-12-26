package com.booking.bookingService.service;

import com.booking.bookingService.Enum.StopType;
import com.booking.bookingService.dto.*;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import com.booking.bookingService.exception.ResourceNotFoundException;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final BusRepository busRepository;
    private final SeatRepository seatRepository;
    private final RouteRepository routeRepository;
    private final TripSeatRepository seatStatusRepository;
    private final RouteStopRepository routeStopRepository;
    private final TripStopRepository tripStopRepository;
    private final StationRepository stationRepository;
    private final RedisLockService redisLockService;

    public Trip createTrip(TripRequest request) {
        validateBusAvailability(request.getBusId(), request.getDepartureTime(), request.getArrivalTime(), null);
        Bus bus = busRepository.findById(request.getBusId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));

        // Ensure Bus belongs to Operator of Route (optional business rule, but good
        // practice)
        // For now, we assume flexible assignment or strict check:
        // if (!bus.getOperator().getId().equals(route.getOperator().getId())) ...

        // if request.discountPrice is null, then set discountPrice to -1
        BigDecimal discount = request.getDiscountPrice() != null ? request.getDiscountPrice() : BigDecimal.ONE.negate();

        Trip trip = Trip.builder()
                .bus(bus)
                .route(route)
                .operator(bus.getOperator()) // Inherit operator from Bus
                .departureTime(request.getDepartureTime())
                .originalPrice(request.getOriginalPrice())
                .discountPrice(discount)
                .status(Trip.TripStatus.SCHEDULED)
                .availableSeats(bus.getSeatCapacity())
                .build();

        Trip savedTrip = tripRepository.save(trip);

        // Initialize Seat Statuses
        List<Seat> physicalSeats = seatRepository.findByBusId(bus.getId());
        List<TripSeat> statuses = physicalSeats.stream().map(seat -> TripSeat.builder()
                .trip(savedTrip)
                .seat(seat)
                .status(TripSeat.Status.AVAILABLE)
                .build()).collect(Collectors.toList());

        seatStatusRepository.saveAll(statuses);

        initializeSeatsForTrip(savedTrip, bus);

        // Handle Stops (Default vs Custom)
        if (request.getStops() != null && !request.getStops().isEmpty()) {
            // Custom list provided by Operator
            createCustomTripStops(savedTrip, request.getStops());
        } else {
            // Default: Copy from Route template
            generateTripStopsFromTemplate(savedTrip, route);
        }

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
        trip.setOriginalPrice(request.getOriginalPrice());
        trip.setDiscountPrice(request.getDiscountPrice() != null ? request.getDiscountPrice() : BigDecimal.ONE.negate());

        if (request.getStatus() != null) {
            try {
                trip.setStatus(Trip.TripStatus.valueOf(request.getStatus()));
            } catch (IllegalArgumentException e) {
                // Ignore or throw invalid status exception
            }
        }

        tripRepository.save(trip);

        if (request.getStops() != null && !request.getStops().isEmpty()) {
            // 1. Delete existing stops
            List<TripStop> oldStops = tripStopRepository.findByTripId(tripId);
            tripStopRepository.deleteAll(oldStops);

            // 2. Create new stops from request
            createCustomTripStops(trip, request.getStops());
        } else {
            // Optional: If you want to allow "Reset to Default" by sending empty list,
            // you could uncomment the logic here. For now, empty list = "No Change"
            // is usually safer for Updates, OR you can force them to send the list every
            // time.
            // Logic below assumes: If provided -> Replace. If null -> Keep existing.
        }

        // NOTE: If bus changes, we technically need to regenerate SeatStatuses.
        // This complexity is omitted for brevity but important in production.

        return trip;
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

            Expression<BigDecimal> effectivePrice = criteriaBuilder.selectCase()
                .when(criteriaBuilder.greaterThan(root.get("discountPrice"), BigDecimal.ONE.negate()), root.get("discountPrice"))
                .otherwise(root.get("originalPrice"))
                .as(BigDecimal.class);

            // 1. Origin
            if (request.getOrigin() != null && !request.getOrigin().isEmpty()) {
                String originPattern = "%" + request.getOrigin().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(routeJoin.get("origin")),
                        originPattern));
            }

            // 2. Destination
            if (request.getDestination() != null && !request.getDestination().isEmpty()) {
                String destPattern = "%" + request.getDestination().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(routeJoin.get("destination")),
                        destPattern));
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
                        request.getPassengers()));
            }

            // 5. Bus Type (using new type field on Bus)
            if (request.getBusTypes() != null && !request.getBusTypes().isEmpty()) {
                // Convert list to lowercase to match DB (if DB stores 'sleeper', 'limousine',
                // etc.)
                List<String> types = request.getBusTypes().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());

                // Use IN clause
                predicates.add(criteriaBuilder.lower(busJoin.get("type")).in(types));
            }

            // 6. Price Range (using new price field)
            if (request.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        effectivePrice,
                        request.getMinPrice()));
            }
            if (request.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        effectivePrice,
                        request.getMaxPrice()));
            }

            // 7. Operators
            if (request.getOperators() != null && !request.getOperators().isEmpty()) {
                predicates.add(operatorJoin.get("name").in(request.getOperators()));
            }

            // 8. Departure Time Slots
            if (request.getDate() != null
                    && (request.getMinDepartureTime() != null || request.getMaxDepartureTime() != null)) {
                LocalDateTime baseDate = request.getDate().atStartOfDay();

                LocalDateTime start = request.getMinDepartureTime() != null
                        ? baseDate.with(request.getMinDepartureTime())
                        : baseDate; // Default to start of day if min not specified

                LocalDateTime end = request.getMaxDepartureTime() != null
                        ? baseDate.with(request.getMaxDepartureTime())
                        : baseDate.plusDays(1).minusNanos(1); // Default to end of day if max not specified

                predicates.add(criteriaBuilder.between(
                        root.get("departureTime"),
                        start,
                        end));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return tripRepository.findAll(spec, pageable).map(this::mapToTripResponse);
    }

    // --- Get Detail ---
    public TripSearchResponse getTripById(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        // Using mapToTripResponse directly as it now fetches stops inside
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
                        TripSeat::getStatus));

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
                    .status(status)
                    .price(trip.getPrice())
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
        // 1. Fetch Stops for this Trip
        List<TripStop> allStops = tripStopRepository.findByTripId(trip.getId());

        List<TripSearchResponse.StopDto> pickupPoints = allStops.stream()
                .filter(stop -> stop.getType() == StopType.PICKUP)
                .sorted(Comparator.comparingInt(TripStop::getOrderIndex))
                .map(this::mapToStopDto)
                .collect(Collectors.toList());

        List<TripSearchResponse.StopDto> dropoffPoints = allStops.stream()
                .filter(stop -> stop.getType() == StopType.DROPOFF)
                .sorted(Comparator.comparingInt(TripStop::getOrderIndex))
                .map(this::mapToStopDto)
                .collect(Collectors.toList());

        // 2. Construct Schedule List
        TripSearchResponse.ScheduleDto scheduleDto = TripSearchResponse.ScheduleDto.builder()
                .hour(String.format("%02d", trip.getDepartureTime().getHour()))
                .minute(String.format("%02d", trip.getDepartureTime().getMinute()))
                .departureTime(trip.getDepartureTime())
                .arrivalTime(trip.getArrivalTime())
                .build();

        // 3. Build Route Name and Stop Lists
        TripSearchResponse.RouteDto routeDto = TripSearchResponse.RouteDto.builder()
                .name(trip.getRoute().getOrigin() + " - " + trip.getRoute().getDestination())
                .durationMinutes(trip.getRoute().getEstimatedMinutes())
                .pickupPoints(pickupPoints)
                .dropoffPoints(dropoffPoints)
                .build();

        TripSearchResponse.StopDto fromStopDto = null;
        TripSearchResponse.StopDto toStopDto = null;
        
        int randomIndex = 0;

        if (!pickupPoints.isEmpty()) {
            randomIndex = ThreadLocalRandom.current().nextInt(pickupPoints.size());
            fromStopDto = pickupPoints.get(randomIndex);
        }

        if (!dropoffPoints.isEmpty()) {
            randomIndex = ThreadLocalRandom.current().nextInt(dropoffPoints.size());
            toStopDto = dropoffPoints.get(randomIndex);
        }

        return TripSearchResponse.builder()
                .tripId(trip.getId())
                .status(trip.getStatus().name())
                .operator(TripSearchResponse.OperatorDto.builder()
                        .name(trip.getOperator().getName())
                        // TODO:
                        // .image(trip.getOperator().getImage())
                        .image("////////////static.vexere.com/c/i/535/xe-tra-lan-vien-VeXeRe-ITSpW3J-1000x600.jpeg")
                        .ratings(TripSearchResponse.OperatorRating.builder()
                                .overall(trip.getOperator().getRating())
                                .reviews(trip.getOperator().getTotalReviews())
                                .build())
                        .build())
                .route(routeDto)
                .duration(routeDto.getDurationMinutes())
                .from(fromStopDto)
                .to(toStopDto)
                .bus(TripSearchResponse.BusDto.builder()
                        .model(trip.getBus().getModel())
                        .type(trip.getBus().getType())
                        .build())
                .schedules(scheduleDto)
                .pricing(TripSearchResponse.PricingDto.builder()
                        .original(trip.getOriginalPrice())
                        .discount(trip.getDiscountPrice())
                        .build())
                .availability(TripSearchResponse.AvailabilityDto.builder()
                        .totalSeats(trip.getBus().getSeatCapacity())
                        .availableSeats(trip.getAvailableSeats())
                        .build())
                .build();
    }

    private TripSearchResponse.StopDto mapToStopDto(TripStop stop) {
        return TripSearchResponse.StopDto.builder()
                .stopId(stop.getId())
                .name(stop.getStation().getName())
                .address(stop.getFullAddress())
                .type(stop.getType().name())
                .time(stop.getTime())
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

    // --- Helper: Create Custom Stops ---
    private void createCustomTripStops(Trip trip, List<TripRequest.TripStopDto> stopDtos) {
        List<TripStop> newStops = new ArrayList<>();

        for (TripRequest.TripStopDto dto : stopDtos) {
            Station station = stationRepository.findById(dto.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + dto.getStationId()));

            StopType type = StopType.valueOf(dto.getType().toUpperCase());

            TripStop stop = TripStop.builder()
                    .trip(trip)
                    .station(station)
                    .type(type)
                    .orderIndex(dto.getOrderIndex())
                    // Calculate absolute time: Departure + Offset
                    .time(trip.getDepartureTime().plusMinutes(dto.getTimeOffsetMinutes()))
                    .build();

            newStops.add(stop);
        }

        tripStopRepository.saveAll(newStops);
    }

    // --- Helper: Default Template ---
    private void generateTripStopsFromTemplate(Trip trip, Route route) {
        List<RouteStop> templates = routeStopRepository.findByRouteIdOrderByOrderIndexAsc(route.getId());

        if (templates.isEmpty())
            return;

        List<TripStop> tripStops = templates.stream().map(rs -> {
            return TripStop.builder()
                    .trip(trip)
                    .station(rs.getStation())
                    .type(rs.getType())
                    .orderIndex(rs.getOrderIndex())
                    .time(trip.getDepartureTime().plusMinutes(rs.getTimeOffsetMinutes()))
                    .build();
        }).collect(Collectors.toList());

        tripStopRepository.saveAll(tripStops);
    }

    // --- Helper: Initialize Seats ---
    private void initializeSeatsForTrip(Trip trip, Bus bus) {
        List<Seat> physicalSeats = seatRepository.findByBusId(bus.getId());
        List<TripSeat> statuses = physicalSeats.stream().map(seat -> TripSeat.builder()
                .trip(trip)
                .seat(seat)
                .status(TripSeat.Status.AVAILABLE)
                .build()).collect(Collectors.toList());

        seatStatusRepository.saveAll(statuses);
    }
}