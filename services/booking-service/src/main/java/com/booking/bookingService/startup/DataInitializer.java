package com.booking.bookingService.startup;

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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bootstraps the application with initial data.
 * UPDATED: Removed cleanup for non-existent 'seat_type' table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final OperatorRepository operatorRepository;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final SeatRepository seatRepository;
    private final SeatStatusRepository seatStatusRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // --- 1. CLEAN UP EXISTING DATA ---
        log.info("Cleaning up existing data to ensure a fresh start...");
        
        // Use native SQL to avoid entity loading issues
        try {
            entityManager.createNativeQuery("TRUNCATE TABLE seat_status CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE trip CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE seat CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE route CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE bus CASCADE").executeUpdate();
            // REMOVED: entityManager.createNativeQuery("TRUNCATE TABLE seat_type CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE operator CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE booking CASCADE").executeUpdate();
            entityManager.createNativeQuery("TRUNCATE TABLE passenger CASCADE").executeUpdate();
            entityManager.flush();
        } catch (Exception e) {
            log.warn("Could not truncate tables (might be first run): {}", e.getMessage());
            // If truncate fails, try delete (slower but works if schema doesn't exist yet)
            entityManager.createNativeQuery("DELETE FROM seat_status").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM trip").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM seat").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM route").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM bus").executeUpdate();
            // REMOVED: entityManager.createNativeQuery("DELETE FROM seat_type").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM operator").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM booking").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM passenger").executeUpdate();
        }

        log.info("All existing data cleared. Initializing new data from JSON file...");

        // --- 2. INITIALIZE NEW DATA ---
        log.info("Bootstrapping database with fresh seed data...");

        try (InputStream inputStream = new ClassPathResource("data/initial_data.json").getInputStream()) {
            InitialData data = objectMapper.readValue(inputStream, InitialData.class);

            // Caches to resolve relationships without repeated DB lookups
            Map<String, Operator> operatorCache = new HashMap<>();
            Map<String, Bus> busCache = new HashMap<>();
            Map<String, Route> routeCache = new HashMap<>();

            // A. Create Operators
            for (OperatorData opData : data.getOperators()) {
                Operator op = Operator.builder()
                        .name(opData.getName())
                        .contactEmail(opData.getEmail())
                        .contactPhone(opData.getPhone())
                        .build();
                operatorRepository.save(op);
                operatorCache.put(opData.getKey(), op);
            }

            // B. Create Buses & Seats
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

            // C. Create Routes
            for (RouteData routeData : data.getRoutes()) {
                Operator op = operatorCache.get(routeData.getOperatorKey());
                if (op == null) continue;

                Route route = Route.builder()
                        .operator(op)
                        .origin(routeData.getOrigin())
                        .destination(routeData.getDestination())
                        .distanceKm(routeData.getDistance())
                        .estimatedMinutes(routeData.getMinutes())
                        .build();
                routeRepository.save(route);
                routeCache.put(routeData.getKey(), route);
            }

            // D. Create Trips & Statuses
            for (TripData tripData : data.getTrips()) {
                Route route = routeCache.get(tripData.getRouteKey());
                Bus bus = busCache.get(tripData.getBusKey());

                if (route == null || bus == null) continue;

                LocalDateTime departure = LocalDateTime.parse(tripData.getDate());
                LocalDateTime arrival = departure.plusMinutes(route.getEstimatedMinutes());

                Trip trip = Trip.builder()
                        .route(route)
                        .bus(bus)
                        .operator(bus.getOperator())
                        .departureTime(departure)
                        .arrivalTime(arrival)
                        .price(tripData.getPrice())
                        .status(Trip.TripStatus.SCHEDULED)
                        .availableSeats(0)
                        .build();
                tripRepository.save(trip);

                int availableSeats = initializeSeatStatusesForTrip(trip, bus);
                trip.setAvailableSeats(availableSeats);
                tripRepository.save(trip);
            }

            log.info("Bootstrap complete. Created {} trips.", data.getTrips().size());

        } catch (Exception e) {
            log.error("Critical failure during data initialization", e);
            throw e;
        }
    }

    private void generatePhysicalSeatsForBus(Bus bus) {
        List<Seat> seats = new ArrayList<>();
        String[] columns = {"A", "B", "C"};
        int seatsPerRow = columns.length;
        int rows = (bus.getSeatCapacity() + seatsPerRow - 1) / seatsPerRow; // ceil division

        if (bus.getOperator() == null) {
            throw new IllegalStateException("Bus operator is null for bus " + bus.getId());
        }

        for (int row = 1; row <= rows; row++) {
            int colIdx = 1;
            for (String col : columns) {
                if (seats.size() >= bus.getSeatCapacity()) break;

                Seat seat = Seat.builder()
                        .bus(bus)
                        .seatCode(col + String.format("%02d", row))
                        .deckNumber(1)
                        .gridRow(row)       // persist row index
                        .gridCol(colIdx)    // persist column index
                        .build();
                seats.add(seat);
                colIdx++;
            }
        }
        seatRepository.saveAll(seats);
    }

    private int initializeSeatStatusesForTrip(Trip trip, Bus bus) {
        List<Seat> seats = seatRepository.findByBusId(bus.getId());
        List<SeatStatus> statuses = new ArrayList<>();
        int availableCount = 0;

        for (Seat seat : seats) {
            boolean isBooked = Math.random() < 0.1;
            SeatStatus.SeatState state = isBooked ? SeatStatus.SeatState.BOOKED : SeatStatus.SeatState.AVAILABLE;
            if (!isBooked) availableCount++;

            SeatStatus status = SeatStatus.builder()
                    .trip(trip)
                    .seat(seat)
                    .state(state)
                    .build();
            statuses.add(status);
        }
        seatStatusRepository.saveAll(statuses);
        return availableCount;
    }

    @Data
    static class InitialData {
        private List<OperatorData> operators;
        private List<BusData> buses;
        private List<RouteData> routes;
        private List<TripData> trips;
    }

    @Data
    static class OperatorData {
        private String key;
        private String name;
        private String email;
        private String phone;
    }

    @Data
    static class BusData {
        private String key;
        private String operatorKey;
        private String model;
        private String plateNumber;
        private int capacity;
        private String type;
    }

    @Data
    static class RouteData {
        private String key;
        private String operatorKey;
        private String origin;
        private String destination;
        private int distance;
        private int minutes;
    }

    @Data
    static class TripData {
        private String routeKey;
        private String busKey;
        private String date;
        private BigDecimal price;
    }
}