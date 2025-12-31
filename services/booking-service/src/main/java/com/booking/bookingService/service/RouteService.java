package com.booking.bookingService.service;

import com.booking.bookingService.dto.route.RouteRequest;
import com.booking.bookingService.dto.route.RouteResponse;
import com.booking.bookingService.dto.route.RouteStopResponse;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Route;
import com.booking.bookingService.model.RouteStop;
import com.booking.bookingService.model.Station;
import com.booking.bookingService.repository.OperatorRepository;
import com.booking.bookingService.repository.RouteRepository;
import com.booking.bookingService.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteService {
    private final RouteRepository routeRepository;
    private final OperatorRepository operatorRepository;
    private final StationRepository stationRepository;

    public RouteResponse createRoute(RouteRequest request) {
        Operator operator = operatorRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        Route route = Route.builder()
                .operator(operator)
                .name(request.getName())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .distanceKm(request.getDistanceKm())
                .estimatedMinutes(request.getEstimatedMinutes())
                .build();

        if (request.getStops() != null) {
            List<RouteStop> stops = request.getStops().stream().map(stopReq -> {
                Station station = stationRepository.findById(stopReq.getStationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
                
                return RouteStop.builder()
                        .route(route)
                        .station(station)
                        .type(stopReq.getType())
                        .duration(stopReq.getDuration())
                        .isOrigin(stopReq.isOrigin())
                        .isDestination(stopReq.isDestination())
                        .build();
            }).toList();
            
            route.setStops(stops);
        }

        Route savedRoute = routeRepository.save(route);
        return mapToDto(savedRoute);
    }

    public List<RouteResponse> getAllRoutes() {
        return routeRepository.findAll().stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
    }

    public RouteResponse getRoute(UUID id) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        return mapToDto(route);
    }

    @Transactional
    public RouteResponse updateRoute(UUID id, RouteRequest request) {
        Route route = routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found"));
        
        // Update basic fields
        route.setName(request.getName());
        route.setOrigin(request.getOrigin());
        route.setDestination(request.getDestination());
        route.setDistanceKm(request.getDistanceKm());
        route.setEstimatedMinutes(request.getEstimatedMinutes());

        // Update Operator if changed
        if (!route.getOperator().getId().equals(request.getOperatorId())) {
             Operator operator = operatorRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
             route.setOperator(operator);
        }

        // Handle Stops Update (Clear and Replace due to orphanRemoval = true)
        if (request.getStops() != null) {
            route.getStops().clear(); // Triggers orphan removal
            request.getStops().forEach(stopReq -> {
                Station station = stationRepository.findById(stopReq.getStationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
                
                route.getStops().add(RouteStop.builder()
                        .route(route)
                        .station(station)
                        .type(stopReq.getType())
                        .duration(stopReq.getDuration())
                        .isOrigin(stopReq.isOrigin())
                        .isDestination(stopReq.isDestination())
                        .build());
            });
        }

        routeRepository.save(route);

        return mapToDto(route);
    }

    public void deleteRoute(UUID id) {
        if (!routeRepository.existsById(id)) throw new ResourceNotFoundException("Route not found");
        routeRepository.deleteById(id);
    }

    // Helper method for mapping
    private RouteResponse mapToDto(Route route) {
        List<RouteStopResponse> stopDtos = route.getStops().stream()
            .map(stop -> RouteStopResponse.builder()
                .id(stop.getId())
                .stationId(stop.getStation().getId())
                .name(stop.getStation().getName())
                .address(stop.getFullAddress()) // Uses logic from model
                .type(stop.getType())
                .duration(stop.getDuration())
                .isOrigin(stop.isOrigin())
                .isDestination(stop.isDestination())
                .build())
            .toList();

        return RouteResponse.builder()
            .id(route.getId())
            .name(route.getName())
            .operatorId(route.getOperator().getId())
            .origin(route.getOrigin())
            .destination(route.getDestination())
            .distanceKm(route.getDistanceKm())
            .estimatedMinutes(route.getEstimatedMinutes())
            .stops(stopDtos)
            .build();
    }
}