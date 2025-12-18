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
 * UPDATED: Added cleanup for Payment/TripStop and automatic TripStop generation.
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
    private final TripSeatRepository seatStatusRepository;
    private final TripStopRepository tripStopRepository; 
    
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // --- 1. CLEAN UP EXISTING DATA ---
        log.info("Cleaning up existing data to ensure a fresh start...");
        
        // Disable constraints temporarily if needed, but CASCADE usually handles it
        // A. Clean up Payment (Child of Ticket)
        entityManager.createNativeQuery("TRUNCATE TABLE payment CASCADE").executeUpdate();

        // B. Clean up Ticket & Seats
        entityManager.createNativeQuery("TRUNCATE TABLE ticket_seats CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE ticket CASCADE").executeUpdate();
        
        // C. Clean up Trip details (Seats & Stops)
        entityManager.createNativeQuery("TRUNCATE TABLE trip_seat CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE trip_stop CASCADE").executeUpdate(); // NEW
        
        // D. Clean up Core Data
        entityManager.createNativeQuery("TRUNCATE TABLE trip CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE seat CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE route CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE bus CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE operator CASCADE").executeUpdate();

        entityManager.flush();

        log.info("All existing data cleared. Initializing new data from JSON file...");

        // --- 2. INITIALIZE NEW DATA ---
        log.info("Bootstrapping database with fresh seed data...");

        try (InputStream inputStream = new ClassPathResource("data/initial_data.json").getInputStream()) {
            InitialData data = objectMapper.readValue(inputStream, InitialData.class);

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

            // D. Create Trips, Statuses & STOPS
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

                // 1. Generate Seat Statuses
                int availableSeats = initializeSeatStatusesForTrip(trip, bus);
                trip.setAvailableSeats(availableSeats);
                tripRepository.save(trip);

                // 2. Generate Trip Stops (NEW)
                generateStopsForTrip(trip, route);
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
        int rows = (bus.getSeatCapacity() + seatsPerRow - 1) / seatsPerRow;

        for (int row = 1; row <= rows; row++) {
            int colIdx = 1;
            for (String col : columns) {
                if (seats.size() >= bus.getSeatCapacity()) break;

                Seat seat = Seat.builder()
                        .bus(bus)
                        .seatCode(col + String.format("%02d", row))
                        .deckNumber(1)
                        .gridRow(row)
                        .gridCol(colIdx)
                        .build();
                seats.add(seat);
                colIdx++;
            }
        }
        seatRepository.saveAll(seats);
    }

    private int initializeSeatStatusesForTrip(Trip trip, Bus bus) {
        List<Seat> seats = seatRepository.findByBusId(bus.getId());
        List<TripSeat> statuses = new ArrayList<>();
        int availableCount = 0;

        for (Seat seat : seats) {
            boolean isBooked = Math.random() < 0.1;
            TripSeat.Status statusValue = isBooked ? TripSeat.Status.BOOKED : TripSeat.Status.AVAILABLE;
            if (!isBooked) availableCount++;

            TripSeat status = TripSeat.builder()
                    .trip(trip)
                    .seat(seat)
                    .status(statusValue)
                    .build();
            statuses.add(status);
        }
        seatStatusRepository.saveAll(statuses);
        return availableCount;
    }

    /**
     * Generates default Pickup and Dropoff points based on the Route.
     */
    private void generateStopsForTrip(Trip trip, Route route) {
        List<TripStop> stops = new ArrayList<>();

        // --- PICKUP 1: Office (30 mins before departure) ---
        stops.add(TripStop.builder()
                .trip(trip)
                .type(TripStop.StopType.PICKUP)
                .placeName("Văn phòng " + route.getOrigin())
                .address("123 Đường Trung Tâm") // Maps to 'street'
                .ward("Phường 1")
                .city(route.getOrigin())
                .time(trip.getDepartureTime().minusMinutes(30))
                .build());

        // --- PICKUP 2: Bus Station (At departure time) ---
        stops.add(TripStop.builder()
                .trip(trip)
                .type(TripStop.StopType.PICKUP)
                .placeName("Bến xe " + route.getOrigin())
                .address("Quầy vé số 5, Bến xe trung tâm")
                .ward("Phường Bến Xe")
                .city(route.getOrigin())
                .time(trip.getDepartureTime())
                .build());

        // --- DROPOFF 1: Destination Bus Station (At arrival time) ---
        stops.add(TripStop.builder()
                .trip(trip)
                .type(TripStop.StopType.DROPOFF)
                .placeName("Bến xe " + route.getDestination())
                .address("Cổng trả khách, Bến xe")
                .ward("Phường Kết Thúc")
                .city(route.getDestination())
                .time(trip.getArrivalTime())
                .build());

        // --- DROPOFF 2: City Center (15 mins after arrival) ---
        stops.add(TripStop.builder()
                .trip(trip)
                .type(TripStop.StopType.DROPOFF)
                .placeName("Trung chuyển Trung tâm " + route.getDestination())
                .address("Công viên thành phố")
                .ward("Phường Trung Tâm")
                .city(route.getDestination())
                .time(trip.getArrivalTime().plusMinutes(15))
                .build());

        tripStopRepository.saveAll(stops);
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