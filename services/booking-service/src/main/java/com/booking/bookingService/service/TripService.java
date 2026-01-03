package com.booking.bookingService.service;

import com.booking.bookingService.Enum.BusType;
import com.booking.bookingService.Enum.StopType;
import com.booking.bookingService.dto.ticket.SeatMapResponse;
import com.booking.bookingService.dto.trip.TripCreateRequest;
import com.booking.bookingService.dto.trip.TripCreateResponse;
import com.booking.bookingService.dto.trip.TripSearchRequest;
import com.booking.bookingService.dto.trip.TripSearchResponse;
import com.booking.bookingService.dto.trip.admin.TripSearchParams;
import com.booking.bookingService.dto.trip.TripCreateResponse.BusDto;
import com.booking.bookingService.dto.trip.TripCreateResponse.BusModelDto;
import com.booking.bookingService.dto.trip.TripCreateResponse.RouteDto;
import com.booking.bookingService.dto.trip.TripDetailsResponse;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    private final FeedbackRepository feedbackRepository;
    private final RedisLockService redisLockService;
    private final BusModelRepository busModelRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public TripCreateResponse createTrip(TripCreateRequest request, UUID currentOperatorId) {
        // 1. Validate Route Ownership
        Route route = routeRepository.findById(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        if (!route.getOperator().getId().equals(currentOperatorId)) {
            throw new IllegalArgumentException("Invalid Route: You do not own this route.");
        }

        Bus bus = null;
        BusModel busModel = null;

        // 2. Determine Bus and BusModel
        if (request.getBusId() != null) {
            bus = busRepository.findById(request.getBusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));

            if (!bus.getOperator().getId().equals(currentOperatorId)) {
                throw new IllegalArgumentException("Invalid Bus: You do not own this bus.");
            }
            // Auto-get BusModel from the physical Bus
            busModel = bus.getModel();
        } else if (request.getBusModelId() != null) {
            // If physical Bus is null, the Operator must provide a BusModel (template)
            busModel = busModelRepository.findById(request.getBusModelId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bus Model not found"));

            if (!busModel.getOperator().getId().equals(currentOperatorId)) {
                throw new IllegalArgumentException("Invalid Bus Model: You do not own this model.");
            }
        } else {
            throw new IllegalArgumentException("Either BusId or BusModelId must be provided.");
        }

        // 3. Handle Pricing (Default discount to -1 if null)
        BigDecimal discount = request.getDiscountPrice() != null ? request.getDiscountPrice() : BigDecimal.valueOf(-1);

        // 4. Build Trip (Status defaults to SCHEDULED)
        Trip trip = Trip.builder()
                .bus(bus) // Can be null
                .busModel(busModel) // Inherited from Bus or provided directly
                .route(route)
                .operator(route.getOperator())
                .departureTime(request.getDepartureTime())
                .originalPrice(request.getOriginalPrice())
                .discountPrice(discount)
                .status(Trip.TripStatus.SCHEDULED) // Requirement: default to SCHEDULED
                .availableSeats(busModel.getSeatCapacity())
                .build();

        Trip savedTrip = tripRepository.save(trip);

        // 5. Initialize Seat status records for the trip
        initializeSeatsForTrip(savedTrip, busModel);

        return mapToCreateResponse(savedTrip);
    }

    /**
     * Retrieves detailed trip information for the Operator,
     * including the Seat Map with Guest (Passenger) details.
     */
    @Transactional(readOnly = true)
    public TripDetailsResponse getTripDetailsForOperator(UUID tripId, UUID currentOperatorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        // 1. Security Check
        validateOwnership(trip, currentOperatorId);

        // 2. Fetch Tickets to map Passengers to Seats
        // We only care about valid tickets (Confirmed, Completed, or Pending if you
        // want to see locked seats)
        List<Ticket> tickets = ticketRepository.findByTripId(tripId);
        Map<String, Ticket> seatTicketMap = new HashMap<>();

        for (Ticket t : tickets) {
            // Filter out cancelled tickets so we don't show invalid passengers
            if (t.getStatus() != Ticket.TicketStatus.CANCELLED && t.getStatus() != Ticket.TicketStatus.CANCELLED) {
                for (String seat : t.getSeats()) {
                    seatTicketMap.put(seat, t);
                }
            }
        }

        // 3. Fetch Physical Seats and Statuses
        // Use getBusModel() to support both Virtual and Physical buses
        List<Seat> physicalSeats = seatRepository.findByBusModelId(trip.getBusModel().getId());
        List<TripSeat> seatStatuses = seatStatusRepository.findByTripId(tripId);

        Map<String, TripSeat.Status> statusMap = seatStatuses.stream()
                .collect(Collectors.toMap(s -> s.getSeat().getSeatCode(), TripSeat::getStatus));

        // 4. Calculate Seat Map Dimensions
        int totalDecks = physicalSeats.stream().mapToInt(Seat::getDeckNumber).max().orElse(1);
        int maxRows = physicalSeats.stream().mapToInt(Seat::getGridRow).max().orElse(0);
        int maxCols = physicalSeats.stream().mapToInt(Seat::getGridCol).max().orElse(0);

        // 5. Map to SeatDetailDto with Passenger info
        List<TripDetailsResponse.SeatDetailDto> seatDetailDtos = physicalSeats.stream().map(seat -> {
            String code = seat.getSeatCode();
            // Get status from DB or default to AVAILABLE
            String status = statusMap.getOrDefault(code, TripSeat.Status.AVAILABLE).name();

            TripDetailsResponse.PassengerDto passenger = null;

            // If the seat is mapped to a ticket, populate passenger info
            if (seatTicketMap.containsKey(code)) {
                Ticket t = seatTicketMap.get(code);
                passenger = TripDetailsResponse.PassengerDto.builder()
                        .name(t.getContactName())
                        .email(t.getContactEmail())
                        .phone(t.getContactPhone())
                        .build();

                // If the ticket is valid, ensure the status reflects it (e.g., if it was
                // PENDING but DB says AVAILABLE due to lag)
                if (status.equals("AVAILABLE")) {
                    status = "BOOKED";
                }
            }

            return TripDetailsResponse.SeatDetailDto.builder()
                    .seatCode(code)
                    .status(status) // e.g., AVAILABLE, BOOKED, LOCKED, MAINTENANCE
                    .row(seat.getGridRow())
                    .col(seat.getGridCol())
                    .deck(seat.getDeckNumber())
                    .passenger(passenger) // Null if no passenger
                    .build();
        }).collect(Collectors.toList());

        // 6. Construct the Nested SeatMapDto
        TripDetailsResponse.SeatMapDto seatMapDto = TripDetailsResponse.SeatMapDto.builder()
                .totalDecks(totalDecks)
                .gridRows(maxRows)
                .gridColumns(maxCols)
                .seats(seatDetailDtos)
                .build();

        // 7. Construct Final Response
        return TripDetailsResponse.builder()
                .id(trip.getId())
                .route(TripDetailsResponse.RouteDto.builder()
                        .id(trip.getRoute().getId())
                        .name(trip.getRoute().getName())
                        .build())
                .bus(trip.getBus() != null ? TripDetailsResponse.BusDto.builder()
                        .id(trip.getBus().getId())
                        .name(trip.getBus().getPlateNumber())
                        .plateNumber(trip.getBus().getPlateNumber())
                        .busModel(TripDetailsResponse.BusModelDto.builder()
                                .id(trip.getBus().getModel().getId())
                                .name(trip.getBus().getModel().getName())
                                .typeDisplay(trip.getBus().getModel().getTypeDisplay())
                                .build())
                        .build() : null)
                .busModel(TripDetailsResponse.BusModelDto.builder()
                        .id(trip.getBusModel().getId())
                        .name(trip.getBusModel().getName())
                        .typeDisplay(trip.getBusModel().getTypeDisplay())
                        .build())
                .departureTime(trip.getDepartureTime())
                .arrivalTime(trip.getArrivalTime())
                .originalPrice(trip.getOriginalPrice())
                .discountPrice(trip.getDiscountPrice())
                .status(trip.getStatus().name())
                .availableSeats(trip.getAvailableSeats())
                .seatMap(seatMapDto)
                .build();
    }

    @Transactional
    public TripCreateResponse updateTrip(UUID tripId, TripCreateRequest request, UUID currentOperatorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        // 1. Security Check
        validateOwnership(trip, currentOperatorId);

        // 2. Update Route (if provided)
        if (request.getRouteId() != null) {
            throw new IllegalArgumentException(
                    "Can not change Route after creation. Please update Trip Status and create another one.");
        }

        // 3. Update Bus Logic

        // Rule A: Trip BusModel is immutable after creation (to preserve seat map
        // integrity)
        if (request.getBusModelId() != null && !request.getBusModelId().equals(trip.getBusModel().getId())) {
            throw new IllegalArgumentException(
                    "Cannot change Bus Model after trip creation. You must use a bus of type: "
                            + trip.getBusModel().getTypeDisplay());
        }

        // Rule B: Assigning a Physical Bus
        if (request.getBusId() != null) {
            Bus bus = busRepository.findById(request.getBusId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));

            if (!bus.getOperator().getId().equals(currentOperatorId)) {
                throw new IllegalArgumentException("Invalid Bus: You do not own this bus.");
            }

            // CHANGED: Allow assignment if the 'TypeDisplay' matches, even if the Model ID
            // is different.
            // This supports substituting vehicles (e.g. swapping one Sleeper for another).
            if (!bus.getModel().getTypeDisplay().equals(trip.getBusModel().getTypeDisplay())) {
                throw new IllegalArgumentException("Bus Type Mismatch: The selected bus type ("
                        + bus.getModel().getTypeDisplay() + ") does not match the trip's required type ("
                        + trip.getBusModel().getTypeDisplay() + ").");
            }

            // Determine time for availability check
            LocalDateTime departureTime = request.getDepartureTime() != null ? request.getDepartureTime()
                    : trip.getDepartureTime();
            LocalDateTime arrivalTime = departureTime.plusMinutes(trip.getRoute().getEstimatedMinutes());

            // Validate Availability
            validateBusAvailability(request.getBusId(), departureTime, arrivalTime, tripId);

            trip.setBus(bus);
            // Note: We DO NOT update trip.setBusModel() here. The trip keeps its original
            // "Template" (Seat Map).
        } else if (request.getBusModelId() != null) {
            // Case: Request has BusModelId (which we verified matches existing) but NO
            // BusId
            // -> User wants to "Unassign" the physical bus (Revert to Virtual/Template
            // only)
            trip.setBus(null);
        }

        // 4. Update Time & Prices
        if (request.getDepartureTime() != null) {
            trip.setDepartureTime(request.getDepartureTime());

            // Re-validate availability if we have a physical bus assigned
            if (trip.getBus() != null) {
                LocalDateTime arrivalTime = request.getDepartureTime()
                        .plusMinutes(trip.getRoute().getEstimatedMinutes());
                validateBusAvailability(trip.getBus().getId(), request.getDepartureTime(), arrivalTime, tripId);
            }
        }

        if (request.getOriginalPrice() != null) {
            trip.setOriginalPrice(request.getOriginalPrice());
        }

        trip.setDiscountPrice((request.getDiscountPrice() != null || request.getDiscountPrice() == BigDecimal.ZERO)
                ? request.getDiscountPrice()
                : BigDecimal.ONE.negate());

        // 5. Update Status
        if (request.getStatus() != null) {
            try {
                Trip.TripStatus newStatus = Trip.TripStatus.valueOf(request.getStatus());
                trip.setStatus(newStatus);
            } catch (IllegalArgumentException e) {
                // Ignore invalid status enum
            }
        }

        return mapToCreateResponse(tripRepository.save(trip));
    }

    // --- Delete Trip ---
    @Transactional
    public void deleteTrip(UUID tripId, UUID currentOperatorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        validateOwnership(trip, currentOperatorId);

        List<TripSeat> statuses = seatStatusRepository.findByTripId(tripId);
        seatStatusRepository.deleteAll(statuses);

        tripRepository.delete(trip);
    }

    // Get Operator's own trips
    public List<TripCreateResponse> getOperatorTrips(UUID operatorId, TripSearchParams params) {
        Specification<Trip> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Mandatory: Filter by Operator Ownership
            predicates.add(cb.equal(root.get("operator").get("id"), operatorId));

            // 2. Filter by Origin (if provided)
            if (params.getOrigin() != null && !params.getOrigin().trim().isEmpty()) {
                String originPattern = "%" + params.getOrigin().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("route").get("origin")), originPattern));
            }

            // 3. Filter by Destination (if provided)
            if (params.getDestination() != null && !params.getDestination().trim().isEmpty()) {
                String destPattern = "%" + params.getDestination().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("route").get("destination")), destPattern));
            }

            // 4. Filter by Date (if provided)
            if (params.getDate() != null) {
                // Create a range for the whole day (00:00:00 to 23:59:59)
                LocalDateTime startOfDay = params.getDate().atStartOfDay();
                LocalDateTime endOfDay = params.getDate().atTime(LocalTime.MAX);

                predicates.add(cb.between(root.get("departureTime"), startOfDay, endOfDay));
            }

            // Sort by Departure Time descending (most recent first)
            query.orderBy(cb.desc(root.get("departureTime")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Use findAll(Specification) which is available because TripRepository extends
        // JpaSpecificationExecutor
        return tripRepository.findAll(spec).stream()
                .map(this::mapToCreateResponse)
                .collect(Collectors.toList());
    }

    private Specification<Trip> sortByEffectivePriceAsc() {
        return (root, query, cb) -> {
            // We must check query.getResultType() to avoid crashing the "Count" query used
            // for pagination
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {

                // Logic: IF discountPrice > -1 THEN discountPrice ELSE originalPrice
                Expression<BigDecimal> effectivePrice = cb.selectCase()
                        .when(cb.greaterThan(root.get("discountPrice"), BigDecimal.valueOf(-1)),
                                root.get("discountPrice"))
                        .otherwise(root.get("originalPrice"))
                        .as(BigDecimal.class);

                // Apply the Order By directly to the query
                query.orderBy(cb.asc(effectivePrice));
            }
            return null; // We return null because we are modifying the query, not adding a WHERE clause
        };
    }

    public Page<TripSearchResponse> searchTrips(TripSearchRequest request) {
        Specification<Trip> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Joins
            var routeJoin = root.join("route");
            var operatorJoin = root.join("operator");
            var modelJoin = root.join("busModel");

            Expression<BigDecimal> effectivePrice = criteriaBuilder.selectCase()
                    .when(criteriaBuilder.greaterThan(root.get("discountPrice"), BigDecimal.ONE.negate()),
                            root.get("discountPrice"))
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

            // 4. Passenger Capacity (using new availableSeats field)
            if (request.getPassengers() != null && request.getPassengers() > 0) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("availableSeats"),
                        request.getPassengers()));
            }

            // 5. Bus Type (using new type field on Bus)
            if (request.getBusTypes() != null && !request.getBusTypes().isEmpty()) {
                List<BusType> types = request.getBusTypes().stream()
                        .map(String::toUpperCase)
                        .map(BusType::valueOf)
                        .collect(Collectors.toList());
                predicates.add(modelJoin.get("type").in(types));
            }
            // 6. Filter by Limousine status
            // Pass a boolean in your request DTO to trigger this
            if (request.getIsLimousine() != null) {
                predicates.add(criteriaBuilder.equal(modelJoin.get("isLimousine"), request.getIsLimousine()));
            }

            // 7. Filter by WC availability
            if (request.getHasWC() != null) {
                predicates.add(criteriaBuilder.equal(modelJoin.get("hasWC"), request.getHasWC()));
            }

            // 8. Price Range (using new price field)
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

            // 9. Operators
            if (request.getOperators() != null && !request.getOperators().isEmpty()) {
                predicates.add(operatorJoin.get("name").in(request.getOperators()));
            }

            // 10. Departure Time filter using UTC time from frontend
            if (request.getDate() != null) {
                // Use timezone from FE, fallback to UTC if not provided
                ZoneId zoneId = (request.getTimezone() != null && !request.getTimezone().isEmpty())
                        ? ZoneId.of(request.getTimezone())
                        : ZoneId.of("UTC");

                // Convert Instant (UTC) to LocalDateTime using FE timezone
                LocalDateTime searchDateTime = LocalDateTime.ofInstant(request.getDate(), zoneId);

                // Apply 30-minute buffer from the provided time
                LocalDateTime cutoffTime = searchDateTime.plusMinutes(30);

                // Get start and end of the search day
                LocalDateTime dayEnd = searchDateTime.toLocalDate().atTime(LocalTime.MAX);

                // Calculate search start time:
                // - If user provided minDepartureTime, use it (converted to LocalDateTime of
                // that day)
                // - But ensure it's not before the 30-minute cutoff
                LocalDateTime searchStart;
                if (request.getMinDepartureTime() != null) {
                    LocalDateTime userMinTime = searchDateTime.toLocalDate().atTime(request.getMinDepartureTime());
                    // Take the later of: user's min time OR cutoff time (30 min from now)
                    searchStart = userMinTime.isAfter(cutoffTime) ? userMinTime : cutoffTime;
                } else {
                    searchStart = cutoffTime;
                }

                // Calculate search end time:
                // - If user provided maxDepartureTime, use it
                // - Otherwise, use end of day
                LocalDateTime searchEnd = (request.getMaxDepartureTime() != null)
                        ? searchDateTime.toLocalDate().atTime(request.getMaxDepartureTime())
                        : dayEnd;

                // Only add predicates if the time range is valid
                if (searchStart.isBefore(searchEnd) || searchStart.isEqual(searchEnd)) {
                    // Filter: departureTime > searchStart
                    predicates.add(criteriaBuilder.greaterThan(root.get("departureTime"), searchStart));
                    // Filter: departureTime <= searchEnd
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("departureTime"), searchEnd));
                } else {
                    // Invalid range - no results should be returned
                    predicates.add(criteriaBuilder.disjunction()); // Always false
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // Setup Sorting & Pagination
        Sort sort = Sort.unsorted();

        if (request.getSort() != null && !request.getSort().isEmpty()) {
            switch (request.getSort()) {
                case "earliest":
                    sort = Sort.by(Sort.Direction.ASC, "departureTime");
                    break;
                case "latest":
                    sort = Sort.by(Sort.Direction.DESC, "departureTime");
                    break;
                case "lowest_price":
                    spec = spec.and(sortByEffectivePriceAsc());
                    sort = Sort.unsorted();
                    break;
                case "highest_rating":
                    sort = Sort.by(Sort.Direction.DESC, "operator.rating");
                    break;
                default:
                    sort = Sort.by(Sort.Direction.ASC, "departureTime");
            }
        }
        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getLimit(), sort);

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
        List<Seat> physicalSeats = seatRepository.findByBusModelId(trip.getBus().getModel().getId());

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

    @Transactional
    public void assignBusToTrip(UUID tripId, UUID busId, UUID operatorId) {
        Trip trip = tripRepository.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        validateOwnership(trip, operatorId);

        Bus bus = busRepository.findById(busId).orElseThrow(() -> new ResourceNotFoundException("Bus not found"));

        // VALIDATION: Physical bus must match the Trip's BusType
        if (!bus.getModel().getId().equals(trip.getBusModel().getId())) {
            throw new IllegalArgumentException(
                    "Bus Type mismatch! This trip requires: " + trip.getBusModel().getName());
        }

        trip.setBus(bus);
        tripRepository.save(trip);
    }

    // --- Helper Mapper ---
    private TripSearchResponse mapToTripResponse(Trip trip) {
        List<RouteStop> routeStops = trip.getRoute().getStops();

        // 1. Map Pickup/Dropoff Stop to Dto
        List<TripSearchResponse.StopDto> pickupPoints = routeStops.stream()
                .map(this::mapToStopDto)
                .collect(Collectors.toList());

        List<TripSearchResponse.StopDto> dropoffPoints = routeStops.stream()
                .filter(stop -> stop.getType() == StopType.DROPOFF)
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

        // Determine the specific Start (From) and End (To) points using Entity flags
        TripSearchResponse.StopDto fromStopDto = routeStops.stream()
                .filter(stop -> stop.isOrigin()) // Check the Entity flag
                .findFirst()
                .map(this::mapToStopDto) // Map the found entity to DTO
                .orElse(!pickupPoints.isEmpty() ? pickupPoints.get(0) : null); // Fallback to first if not found

        TripSearchResponse.StopDto toStopDto = routeStops.stream()
                .filter(stop -> stop.isDestination()) // Check the Entity flag
                .findFirst()
                .map(this::mapToStopDto) // Map the found entity to DTO
                .orElse(!dropoffPoints.isEmpty() ? dropoffPoints.get(dropoffPoints.size() - 1) : null); // Fallback to
                                                                                                        // last

        return TripSearchResponse.builder()
                .tripId(trip.getId())
                .status(trip.getStatus().name())
                .operator(TripSearchResponse.OperatorDto.builder()
                        .id(trip.getOperator().getId())
                        .name(trip.getOperator().getName())
                        .image(trip.getOperator().getImage())
                        .ratings(TripSearchResponse.OperatorRating.builder()
                                .overall(trip.getOperator().getRating())
                                .reviews(feedbackRepository.countByOperatorId(trip.getOperator().getId()).intValue())
                                .build())
                        .build())
                .route(routeDto)
                .duration(routeDto.getDurationMinutes())
                .from(fromStopDto)
                .to(toStopDto)
                // Use BusModel from Trip directly
                .bus(TripSearchResponse.BusDto.builder()
                        .model(trip.getBusModel().getName()) // Safe access
                        .type(trip.getBusModel().getTypeDisplay()) // Safe access
                        .build())
                .schedules(scheduleDto)
                .pricing(TripSearchResponse.PricingDto.builder()
                        .original(trip.getOriginalPrice())
                        .discount(trip.getDiscountPrice())
                        .build())
                .availability(TripSearchResponse.AvailabilityDto.builder()
                        // Use BusModel from Trip directly
                        .totalSeats(trip.getBusModel().getSeatCapacity()) // Safe access
                        .availableSeats(trip.getAvailableSeats())
                        .build())
                .build();
    }

    private TripSearchResponse.StopDto mapToStopDto(RouteStop stop) {
        return TripSearchResponse.StopDto.builder()
                .stopId(stop.getId())
                .name(stop.getStation().getName())
                .address(stop.getFullAddress())
                .duration(stop.getDuration())
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

    // --- Helper: Initialize Seats ---
    private void initializeSeatsForTrip(Trip trip, BusModel busModel) {
        List<Seat> physicalSeats = seatRepository.findByBusModelId(busModel.getId());
        List<TripSeat> statuses = physicalSeats.stream().map(seat -> TripSeat.builder()
                .trip(trip)
                .seat(seat)
                .status(TripSeat.Status.AVAILABLE)
                .build()).collect(Collectors.toList());

        seatStatusRepository.saveAll(statuses);
    }

    // --- Security Helper ---
    private void validateOwnership(Trip trip, UUID currentOperatorId) {
        if (!trip.getOperator().getId().equals(currentOperatorId)) {
            throw new RuntimeException("Access Denied: You do not own this trip");
        }
    }

    private TripCreateResponse mapToCreateResponse(Trip trip) {
        // Map Route (Simplified or using RouteService logic if available)
        RouteDto routeResponse = RouteDto.builder()
                .id(trip.getRoute().getId())
                .name(trip.getRoute().getName())
                .build();

        BusModelDto busModelResponse = null;
        if (trip.getBusModel() != null) {
            BusModel busModel = trip.getBusModel();

            busModelResponse = BusModelDto.builder()
                    .id(busModel.getId())
                    .name(busModel.getName())
                    .typeDisplay(busModel.getTypeDisplay())
                    .build();
        }

        // Map Bus (Handle null case)
        BusDto busResponse = null;
        if (trip.getBus() != null) {
            busResponse = BusDto.builder()
                    .id(trip.getBus().getId())
                    .plateNumber(trip.getBus().getPlateNumber())
                    .busModel(busModelResponse)
                    .build();
        }

        return TripCreateResponse.builder()
                .id(trip.getId())
                .route(routeResponse)
                .busModel(busModelResponse)
                .bus(busResponse)
                .departureTime(trip.getDepartureTime())
                .originalPrice(trip.getOriginalPrice())
                .discountPrice(trip.getDiscountPrice())
                .status(trip.getStatus().name())
                .availableSeats(trip.getAvailableSeats())
                .build();
    }
}