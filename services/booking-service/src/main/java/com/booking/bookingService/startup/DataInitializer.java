package com.booking.bookingService.startup;

import com.booking.bookingService.Enum.BusType;
import com.booking.bookingService.Enum.StopType;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final OperatorRepository operatorRepository;
    private final BusRepository busRepository;
    private final BusModelRepository busModelRepository; // Added dependency
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final SeatRepository seatRepository;
    private final TripSeatRepository seatStatusRepository;
    private final StationRepository stationRepository;
    private final RouteStopRepository routeStopRepository;
    private final TicketRepository ticketRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.data.init-strategy:IF_EMPTY}")
    private String initStrategy;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Check for command line override flag
        boolean forceArg = Arrays.asList(args).contains("--force-init");
        
        // Determine action based on Strategy and Args
        if ("NEVER".equalsIgnoreCase(initStrategy) && !forceArg) {
            log.info("Data initialization is DISABLED (strategy=NEVER).");
            return;
        }

        boolean shouldInitialize = false;

        if (forceArg || "ALWAYS".equalsIgnoreCase(initStrategy)) {
            log.info("Forcing data initialization (Strategy: ALWAYS or --force-init flag detected).");
            shouldInitialize = true;
        } else {
            // Default: IF_EMPTY
            long count = operatorRepository.count();
            if (count == 0) {
                log.info("Database is empty. Starting initialization (Strategy: IF_EMPTY).");
                shouldInitialize = true;
            } else {
                log.info("Database already contains {} operators. Skipping initialization. (Set app.data.init-strategy=ALWAYS to force reset)", count);
                shouldInitialize = false;
            }
        }

        if (!shouldInitialize) return;

        log.info("Cleaning up existing data...");
        // Cleanup hierarchy: Child -> Parent
        entityManager.createNativeQuery("TRUNCATE TABLE payment CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE ticket_seats CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE ticket CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE trip_seat CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE route_stop CASCADE").executeUpdate();
        
        entityManager.createNativeQuery("TRUNCATE TABLE trip CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE seat CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE route CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE bus CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE bus_model CASCADE").executeUpdate(); // Added
        entityManager.createNativeQuery("TRUNCATE TABLE station CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE operator CASCADE").executeUpdate();
        entityManager.flush();

        log.info("Initializing new data...");

        try (InputStream inputStream = new ClassPathResource("data/initial_data.json").getInputStream()) {
            InitialData data = objectMapper.readValue(inputStream, InitialData.class);

            Map<String, Operator> operatorCache = new HashMap<>();
            Map<String, BusModel> busModelCache = new HashMap<>(); // Changed from busCache
            Map<String, Bus> busCache = new HashMap<>();
            Map<String, Route> routeCache = new HashMap<>();
            Map<String, Station> stationCache = new HashMap<>();

            // 1. Create Operators
            for (OperatorData opData : data.getOperators()) {
                Operator op = Operator.builder()
                        .name(opData.getName())
                        .contactEmail(opData.getEmail())
                        .contactPhone(opData.getPhone())
                        .image(opData.getImage())
                        .rating(opData.getRating())
                        .build();
                operatorRepository.save(op);
                operatorCache.put(opData.getKey(), op);
            }

            // 2. Create Stations (Needed before RouteStops)
            if (data.getStations() != null) {
                for (StationData stData : data.getStations()) {
                    Operator op = operatorCache.get(stData.getOperatorKey());
                    if (op == null) continue;
                    Station station = Station.builder()
                            .operator(op)
                            .name(stData.getName())
                            .address(stData.getAddress())
                            .ward(stData.getWard())
                            .city(stData.getCity())
                            .build();
                    stationRepository.save(station);
                    stationCache.put(stData.getKey(), station);
                }
            }

            // 3. Create Bus Models (Templates)
            if (data.getBusModels() != null) {
                for (BusModelData modelData : data.getBusModels()) {
                    Operator op = operatorCache.get(modelData.getOperatorKey());
                    if (op == null) continue;

                    BusType type = BusType.SLEEPER;
                    try {
                        type = BusType.valueOf(modelData.getType().toUpperCase());
                    } catch (Exception e) {}

                    int decks = 2;
                    int rows = 6;
                    int cols = 3;

                    BusModel model = BusModel.builder()
                            .operator(op)
                            .name(modelData.getName())
                            .seatCapacity(modelData.getCapacity())
                            .type(type)
                            .isLimousine(Boolean.TRUE.equals(modelData.getIsLimousine()))
                            .hasWC(modelData.getHasWC())
                            .totalDecks(decks)
                            .gridRows(rows)
                            .gridColumns(cols)
                            .build();
                    
                    busModelRepository.save(model);
                    busModelCache.put(modelData.getKey(), model);
                    
                    // Generate Template Seats for this Model
                    generateSeatsForModel(model);
                }
            }

            // 4. Create Physical Buses
            for (BusData busData : data.getBuses()) {
                Operator op = operatorCache.get(busData.getOperatorKey());
                BusModel model = busModelCache.get(busData.getModelKey());
                
                if (op == null || model == null) continue;

                Bus bus = Bus.builder()
                        .operator(op)
                        .model(model)
                        .plateNumber(busData.getPlateNumber())
                        .isActive(true)
                        .build();
                busRepository.save(bus);
                busCache.put(busData.getKey(), bus);
                // No need to generate seats for physical bus anymore!
            }

            // 5. Create Routes
            for (RouteData routeData : data.getRoutes()) {
                Operator op = operatorCache.get(routeData.getOperatorKey());
                if (op == null) continue;
                Route route = Route.builder()
                        .operator(op)
                        .name(routeData.getName())
                        .origin(routeData.getOrigin())
                        .destination(routeData.getDestination())
                        .distanceKm(routeData.getDistance())
                        .estimatedMinutes(routeData.getMinutes())
                        .build();
                routeRepository.save(route);
                routeCache.put(routeData.getKey(), route);
            }

            // 6. Create Route Stops
            if (data.getRouteStops() != null) {
                for (RouteStopData rsData : data.getRouteStops()) {
                    Route route = routeCache.get(rsData.getRouteKey());
                    Station station = stationCache.get(rsData.getStationKey());
                    if (route == null || station == null) continue;
                    RouteStop rs = RouteStop.builder()
                            .route(route)
                            .station(station)
                            .type(StopType.valueOf(rsData.getType()))
                            .duration(rsData.getDuration())
                            .isOrigin(rsData.getIsOrigin())
                            .isDestination(rsData.getIsDestination())
                            .build();
                    routeStopRepository.save(rs);
                }
            }

            // 7. GENERATE MASS TRIPS (75 per day)
            // Target Route: HCM -> Hanoi
            generateMassTrips(routeCache, busCache, 
                LocalDate.of(2026, 1, 2), 
                LocalDate.of(2026, 1, 7), 
                75
            );
            
            log.info("Data initialization complete.");

        } catch (Exception e) {
            log.error("Data initialization failed", e);
            throw e;
        }
    }

    private void generateMassTrips(
            Map<String, Route> routeCache, 
            Map<String, Bus> busCache, 
            LocalDate startDate, 
            LocalDate endDate, 
            int tripsPerDay
    ) {
        log.info("Generating {} trips/day from {} to {}...", tripsPerDay, startDate, endDate);
        
        String[] targetRoutes = {"route_tb_hcm_hn", "route_kumho_hcm_hn", "route_hl_hcm_hn", "route_lh_hcm_hn"};
        List<Bus> allBuses = new ArrayList<>(busCache.values());
        Random random = new Random();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            
            int intervalMinutes = (24 * 60) / tripsPerDay; 
            LocalTime time = LocalTime.of(0, 0);

            for (int i = 0; i < tripsPerDay; i++) {
                String routeKey = targetRoutes[random.nextInt(targetRoutes.length)];
                Route route = routeCache.get(routeKey);
                
                Bus bus = findBusForOperator(allBuses, route.getOperator());
                
                if (route != null && bus != null) {
                    LocalDateTime departure = LocalDateTime.of(current, time);
                    BigDecimal basePrice = BigDecimal.valueOf(5000 + random.nextInt(15000)); 

                    Trip trip = Trip.builder()
                            .route(route)
                            .bus(bus)
                            .busModel(bus.getModel()) // Correctly set Model from Bus
                            .operator(bus.getOperator())
                            .departureTime(departure)
                            .originalPrice(basePrice)
                            .status(Trip.TripStatus.SCHEDULED)
                            .availableSeats(bus.getModel().getSeatCapacity())
                            .build();
                    tripRepository.save(trip);

                    int availableSeats = initializeSeatStatusesAndTicketsForTrip(trip, bus);
                    trip.setAvailableSeats(availableSeats);
                    tripRepository.save(trip);
                }
                time = time.plusMinutes(intervalMinutes);
            }
            log.info("Generated trips for date: {}", current);
            current = current.plusDays(1);
        }
    }

    private Bus findBusForOperator(List<Bus> allBuses, Operator operator) {
        return allBuses.stream()
                .filter(b -> b.getOperator().getId().equals(operator.getId()))
                .findAny()
                .orElse(null);
    }

    // UPDATED: Generate seats for MODEL, not Physical Bus
    private void generateSeatsForModel(BusModel busModel) {
        List<Seat> seats = new ArrayList<>();
        int decks = busModel.getTotalDecks();
        int rows = busModel.getGridRows();
        int cols = busModel.getGridColumns();

        for (int d = 1; d <= decks; d++) {
            for (int r = 1; r <= rows; r++) {
                for (int c = 1; c <= cols; c++) {
                    // Stop if we reach the defined capacity (optional, depending on business logic)
                    if (seats.size() >= busModel.getSeatCapacity()) break;

                    // Generate Seat Code: 
                    // Deck 1 -> A01, A02... 
                    // Deck 2 -> B01, B02...
                    String deckPrefix = (d == 1) ? "A" : "B";
                    // Calculate a sequential number for the seat code, or just use Row/Col
                    // Example: A01, A02, A03... based on position in the grid
                    int seatNum = ((r - 1) * cols) + c; 
                    String seatCode = String.format("%s%02d", deckPrefix, seatNum);

                    seats.add(Seat.builder()
                        .busModel(busModel)
                        .seatCode(seatCode)
                        .deckNumber(d)
                        .gridRow(r)
                        .gridCol(c)
                        .build());
                }
            }
        }
        seatRepository.saveAll(seats);
    }

    private int initializeSeatStatusesAndTicketsForTrip(Trip trip, Bus bus) {
        // UPDATED: Fetch from Model ID
        List<Seat> seats = seatRepository.findByBusModelId(bus.getModel().getId());
        
        List<TripSeat> statuses = new ArrayList<>();
        int availableCount = 0;

        List<RouteStop> stops = routeStopRepository.findByRouteId(trip.getRoute().getId());
        RouteStop pickup = stops.stream().filter(RouteStop::isOrigin).findFirst().orElse(!stops.isEmpty() ? stops.get(0) : null);
        RouteStop dropoff = stops.stream().filter(RouteStop::isDestination).findFirst().orElse(!stops.isEmpty() ? stops.get(stops.size()-1) : null);

        for (Seat seat : seats) {
            boolean isBooked = Math.random() < 0.1;
            TripSeat.Status status = isBooked ? TripSeat.Status.BOOKED : TripSeat.Status.AVAILABLE;
            statuses.add(TripSeat.builder().trip(trip).seat(seat).status(status).build());

            if (!isBooked) {
                availableCount++;
            } else {
                createSeedTicket(trip, seat, pickup, dropoff);
            }
        }
        seatStatusRepository.saveAll(statuses);
        return availableCount;
    }

    // createSeedTicket remains largely the same
    private void createSeedTicket(Trip trip, Seat seat, RouteStop pickup, RouteStop dropoff) {
        try {
            Ticket ticket = Ticket.builder()
                .ticketCode("SEED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .userEmail("seed.user@vexesieure.codes")
                .trip(trip)
                .contactName("Seed User")
                .contactEmail("seed@test.com")
                .contactPhone("0909000111")
                .totalAmount(trip.getOriginalPrice())
                .status(Ticket.TicketStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .confirmedAt(LocalDateTime.now())
                .seats(List.of(seat.getSeatCode()))
                .pickupRouteStop(pickup)
                .dropoffRouteStop(dropoff)
                .build();
            
            ticketRepository.save(ticket);

            CashPayment payment = CashPayment.builder()
                .ticket(ticket)
                .amount(ticket.getTotalAmount())
                .status(Payment.PaymentStatus.PAID)
                .createdAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now())
                .build();
            
            paymentRepository.save(payment);
        } catch (Exception e) {
            log.warn("Failed to create seed ticket for seat {}", seat.getSeatCode());
        }
    }

    @Data
    static class InitialData {
        private List<OperatorData> operators;
        private List<BusModelData> busModels; // Added
        private List<BusData> buses;
        private List<StationData> stations;
        private List<RouteData> routes;
        private List<RouteStopData> routeStops;
    }
    
    // New DTO
    @Data static class BusModelData { 
        private String key; 
        private String operatorKey; 
        private String name; 
        private int capacity; 
        private String type; 
        private Boolean isLimousine; 
        private Boolean hasWC;
    }

    // Modified BusData (removed capacity/type)
    @Data static class BusData { 
        private String key; 
        private String operatorKey; 
        private String modelKey; // Links to BusModel
        private String plateNumber; 
    }
    
    // Other DTOs same as before
    @Data static class OperatorData { private String key; private String name; private String email; private String phone; private double rating; private String image; }
    @Data static class StationData { private String key; private String operatorKey; private String name; private String address; private String ward; private String city; }
    @Data static class RouteData { private String key; private String operatorKey; private String name; private String origin; private String destination; private int distance; private int minutes; private Integer totalDecks; private Integer gridRows; private Integer gridColumns; }
    @Data static class RouteStopData { private String routeKey; private String stationKey; private String type; private int duration; private Boolean isOrigin; Boolean isDestination; }
}