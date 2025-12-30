package com.booking.bookingService.startup;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final OperatorRepository operatorRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final SeatRepository seatRepository;
    private final TripSeatRepository seatStatusRepository;
    private final StationRepository stationRepository;
    private final RouteStopRepository routeStopRepository;
    
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
        entityManager.createNativeQuery("TRUNCATE TABLE station CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE operator CASCADE").executeUpdate();
        entityManager.flush();

        log.info("Initializing new data...");

        try (InputStream inputStream = new ClassPathResource("data/initial_data.json").getInputStream()) {
            InitialData data = objectMapper.readValue(inputStream, InitialData.class);

            Map<String, Operator> operatorCache = new HashMap<>();
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

            // 3. Create Buses
            for (BusData busData : data.getBuses()) {
                Operator op = operatorCache.get(busData.getOperatorKey());
                if (op == null) continue;
                Bus bus = Bus.builder()
                        .operator(op)
                        .model(busData.getModel())
                        .plateNumber(busData.getPlateNumber())
                        .seatCapacity(busData.getCapacity())
                        .type(busData.getType())
                        .build();
                busRepository.save(bus);
                busCache.put(busData.getKey(), bus);
                generatePhysicalSeatsForBus(bus);
            }

            // 4. Create Routes
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

            // 5. Create Route Stops
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

            // 6. GENERATE MASS TRIPS (75 per day)
            // Target Route: HCM -> Hanoi
            generateMassTrips(routeCache, busCache, 
                LocalDate.of(2025, 12, 27), 
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
        
        // Define which routes to use
        String[] targetRoutes = {"route_futa_hcm_hn", "route_tb_hcm_hn", "route_kumho_hcm_hn", "route_hl_hcm_hn", "route_lh_hcm_hn"};
        List<Bus> allBuses = new ArrayList<>(busCache.values());
        Random random = new Random();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            
            // Distribute 75 trips across 24 hours (approx every 19-20 mins)
            int intervalMinutes = (24 * 60) / tripsPerDay; 
            LocalTime time = LocalTime.of(0, 0);

            for (int i = 0; i < tripsPerDay; i++) {
                // Pick a random route (simulating multiple operators)
                String routeKey = targetRoutes[random.nextInt(targetRoutes.length)];
                Route route = routeCache.get(routeKey);
                
                // Pick a bus belonging to that operator
                // (In a real app, you'd check availability, here we just round-robin or random)
                Bus bus = findBusForOperator(allBuses, route.getOperator());
                
                if (route != null && bus != null) {
                    LocalDateTime departure = LocalDateTime.of(current, time);
                    
                    // Vary price slightly
                    BigDecimal basePrice = BigDecimal.valueOf(5000 + random.nextInt(15000)); 

                    Trip trip = Trip.builder()
                            .route(route)
                            .bus(bus)
                            .operator(bus.getOperator())
                            .departureTime(departure)
                            .originalPrice(basePrice)
                            .status(Trip.TripStatus.SCHEDULED)
                            .availableSeats(0)
                            .build();
                    tripRepository.save(trip);

                    int availableSeats = initializeSeatStatusesForTrip(trip, bus);
                    trip.setAvailableSeats(availableSeats);
                    tripRepository.save(trip);
                }
                
                // Advance time
                time = time.plusMinutes(intervalMinutes);
            }
            log.info("Generated trips for date: {}", current);
            current = current.plusDays(1);
        }
    }

    private Bus findBusForOperator(List<Bus> allBuses, Operator operator) {
        return allBuses.stream()
                .filter(b -> b.getOperator().getId().equals(operator.getId()))
                .findAny() // In real logic, rotate or check schedule
                .orElse(null);
    }

    private void generatePhysicalSeatsForBus(Bus bus) {
        // ... (Same as previous) ...
        List<Seat> seats = new ArrayList<>();
        String[] columns = {"A", "B", "C"};
        int seatsPerRow = columns.length;
        int rows = (bus.getSeatCapacity() + seatsPerRow - 1) / seatsPerRow;

        for (int row = 1; row <= rows; row++) {
            int colIdx = 1;
            for (String col : columns) {
                if (seats.size() >= bus.getSeatCapacity()) break;
                seats.add(Seat.builder().bus(bus).seatCode(col + String.format("%02d", row)).deckNumber(1).gridRow(row).gridCol(colIdx++).build());
            }
        }
        seatRepository.saveAll(seats);
    }

    private int initializeSeatStatusesForTrip(Trip trip, Bus bus) {
        // ... (Same as previous) ...
        List<Seat> seats = seatRepository.findByBusId(bus.getId());
        List<TripSeat> statuses = new ArrayList<>();
        int availableCount = 0;
        for (Seat seat : seats) {
            boolean isBooked = Math.random() < 0.1;
            if (!isBooked) availableCount++;
            statuses.add(TripSeat.builder().trip(trip).seat(seat).status(isBooked ? TripSeat.Status.BOOKED : TripSeat.Status.AVAILABLE).build());
        }
        seatStatusRepository.saveAll(statuses);
        return availableCount;
    }

    @Data
    static class InitialData {
        private List<OperatorData> operators;
        private List<BusData> buses;
        private List<StationData> stations;
        private List<RouteData> routes;
        private List<RouteStopData> routeStops;
    }
    // DTO classes same as before
    @Data static class OperatorData { private String key; private String name; private String email; private String phone; private double rating; private String image; }
    @Data static class BusData { private String key; private String operatorKey; private String model; private String plateNumber; private int capacity; private String type; }
    @Data static class StationData { private String key; private String operatorKey; private String name; private String address; private String ward; private String city; }
    @Data static class RouteData { private String key; private String operatorKey; private String name; private String origin; private String destination; private int distance; private int minutes; }
    @Data static class RouteStopData { private String routeKey; private String stationKey; private String type; private int duration; private Boolean isOrigin; Boolean isDestination; }
}