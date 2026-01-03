package com.booking.bookingService.service;

import com.booking.bookingService.Enum.StopType;
import com.booking.bookingService.dto.route.RouteRequest;
import com.booking.bookingService.dto.route.RouteResponse;
import com.booking.bookingService.dto.route.RouteResponse.DetailsDto;
import com.booking.bookingService.dto.route.RouteStopRequest;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Route;
import com.booking.bookingService.model.RouteStop;
import com.booking.bookingService.model.Station;
import com.booking.bookingService.repository.OperatorRepository;
import com.booking.bookingService.repository.RouteRepository;
import com.booking.bookingService.repository.StationRepository;
import com.booking.bookingService.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {
    private final RouteRepository routeRepository;
    private final OperatorRepository operatorRepository;
    private final StationRepository stationRepository;
    private final TripRepository tripRepository;

    @Transactional
    public RouteResponse createRoute(RouteRequest request, UUID currentOperatorId) {
       Operator operator = operatorRepository.findById(currentOperatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));

        Route route = Route.builder()
                .operator(operator)
                .name(request.getName())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .distanceKm(request.getDistanceKm())
                .estimatedMinutes(request.getEstimatedMinutes())
                .stops(new ArrayList<>())
                .isActive(request.getIsActive())
                .build();

        List<RouteStop> allStops = new ArrayList<>();

        // 1. Process Pick-up Stops
        if (request.getPickupStops() != null) {
            allStops.addAll(request.getPickupStops().stream()
                .map(stopReq -> createRouteStopEntity(route, stopReq, StopType.PICKUP))
                .toList());
        }

        // 2. Process Drop-off Stops
        if (request.getDropoffStops() != null) {
            allStops.addAll(request.getDropoffStops().stream()
                .map(stopReq -> createRouteStopEntity(route, stopReq, StopType.DROPOFF))
                .toList());
        }

        route.setStops(allStops);

        Route savedRoute = routeRepository.save(route);
        return mapToDto(savedRoute);
    }

    public List<RouteResponse> getAllRoutes(UUID currentOperatorId) {
        return routeRepository.findAllByOperatorId(currentOperatorId).stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    public RouteResponse getRoute(UUID id, UUID currentOperatorId) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        validateOwnership(route, currentOperatorId);
        return mapToDto(route);
    }

    @Transactional
    public RouteResponse updateRoute(UUID id, RouteRequest request, UUID currentOperatorId) {
        // 1. Fetch original route and validate ownership
        Route oldRoute = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        validateOwnership(oldRoute, currentOperatorId);

        // 2. Check if the route is linked to ANY trip (past or future)
        // If it has trips, we MUST create a new record to preserve history
        boolean hasTrips = tripRepository.existsByRouteId(id);

        if (hasTrips) {
            // SOFT UPDATE: Deactivate old and create new
            oldRoute.setActive(false); // Assuming you have an 'isActive' field
            routeRepository.save(oldRoute);

            // Create the new version using your existing createRoute logic
            return createRoute(request, currentOperatorId);
        } else {
            // HARD UPDATE: Safe to edit directly since no trips use it yet
            oldRoute.setName(request.getName());
            oldRoute.setOrigin(request.getOrigin());
            oldRoute.setDestination(request.getDestination());
            oldRoute.setDistanceKm(request.getDistanceKm());
            oldRoute.setEstimatedMinutes(request.getEstimatedMinutes());

            // Update stops using the clear/addAll pattern to handle orphanRemoval
            oldRoute.getStops().clear(); 
            
            List<RouteStop> newStops = new ArrayList<>();
            if (request.getPickupStops() != null) {
                newStops.addAll(request.getPickupStops().stream()
                    .map(s -> createRouteStopEntity(oldRoute, s, StopType.PICKUP)).toList());
            }
            if (request.getDropoffStops() != null) {
                newStops.addAll(request.getDropoffStops().stream()
                    .map(s -> createRouteStopEntity(oldRoute, s, StopType.DROPOFF)).toList());
            }
            oldRoute.getStops().addAll(newStops);

            return mapToDto(routeRepository.save(oldRoute));
        }
    }

    @Transactional
    public void deleteRoute(UUID id, UUID currentOperatorId) {
            Route route = routeRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
            validateOwnership(route, currentOperatorId);
            routeRepository.delete(route);
        }

        // Helper method to reduce code duplication
    private RouteStop createRouteStopEntity(Route route, RouteStopRequest stopReq, StopType type) {
        Station station = stationRepository.findById(stopReq.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        return RouteStop.builder()
                .route(route)
                .station(station)
                .type(type) // Explicitly set based on the list it came from
                .duration(stopReq.getDuration())
                .isOrigin(stopReq.isOrigin())
                .isDestination(stopReq.isDestination())
                .build();
    }

    // Helper method for mapping
    private RouteResponse mapToDto(Route route) {
        List<RouteStop> routeStops = route.getStops();

        List<RouteResponse.StopDto> pickupPoints = routeStops.stream()
                .filter(stop -> stop.getType() == StopType.PICKUP)
                .map(this::mapToStopDto)
                .collect(Collectors.toList());

        List<RouteResponse.StopDto> dropoffPoints = routeStops.stream()
                .filter(stop -> stop.getType() == StopType.DROPOFF)
                .map(this::mapToStopDto)
                .collect(Collectors.toList());
        
        RouteResponse.StopDto fromDto = routeStops.stream()
                .filter(stop -> stop.isOrigin())
                .findFirst()
                .map(this::mapToStopDto)
                .orElse(null);

        RouteResponse.StopDto toDto = routeStops.stream()
                .filter(stop -> stop.isDestination())
                .findFirst()
                .map(this::mapToStopDto)
                .orElse(null);
        
        return RouteResponse.builder()
            .id(route.getId())
            .details(DetailsDto.builder()
                .name(route.getName())
                .origin(route.getOrigin())
                .destination(route.getDestination())
                .distanceKm(route.getDistanceKm())
                .estimatedMinutes(route.getEstimatedMinutes())
                .build()
            )
            .pickupPoints(pickupPoints)
            .dropoffPoints(dropoffPoints)
            .from(fromDto)
            .to(toDto)
            .active(route.isActive())
            .build();
    }

    private void validateOwnership(Route route, UUID currentOperatorId) {
        if (!route.getOperator().getId().equals(currentOperatorId)) {
            throw new RuntimeException("Access Denied: You do not own this route");
        }
    }

    private RouteResponse.StopDto mapToStopDto(RouteStop stop) {
        return RouteResponse.StopDto.builder()
                .id(stop.getStation().getId())
                .name(stop.getStation().getName())
                .address(stop.getFullAddress())
                .duration(stop.getDuration())
                .build();
    }
}