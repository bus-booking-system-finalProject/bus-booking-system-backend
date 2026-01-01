package com.booking.bookingService.service;

import com.booking.bookingService.Enum.StopType;
import com.booking.bookingService.dto.common.PaginationDto;
import com.booking.bookingService.dto.route.RouteRequest;
import com.booking.bookingService.dto.route.RouteResponse;
import com.booking.bookingService.dto.route.RouteSearchRequest;
import com.booking.bookingService.dto.route.RouteSearchResponse;
import com.booking.bookingService.dto.route.RouteResponse.DetailsDto;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Route;
import com.booking.bookingService.model.RouteStop;
import com.booking.bookingService.model.Station;
import com.booking.bookingService.repository.OperatorRepository;
import com.booking.bookingService.repository.RouteRepository;
import com.booking.bookingService.repository.StationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.data.jpa.domain.Specification;
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
        List<RouteStop> routeStops = route.getStops();

        List<RouteResponse.StopDto> pickupPoints = routeStops.stream()
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
        
        RouteResponse.OperatorDto operator = RouteResponse.OperatorDto.builder()
            .id(route.getOperator().getId())
            .name(route.getOperator().getName())
            .image(route.getOperator().getImage())
            .build();

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
            .operator(operator)
            .build();
    }

    public RouteSearchResponse searchRoutes(RouteSearchRequest request) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getLimit());

        Specification<Route> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search by Name (Partial match, case-insensitive)
            if (request.getName() != null && !request.getName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + request.getName().toLowerCase() + "%"));
            }

            // Filter by Origin
            if (request.getOrigin() != null && !request.getOrigin().isEmpty()) {
                predicates.add(cb.equal(root.get("origin"), request.getOrigin()));
            }

            // Filter by Destination
            if (request.getDestination() != null && !request.getDestination().isEmpty()) {
                predicates.add(cb.equal(root.get("destination"), request.getDestination()));
            }

            // Filter by Operator
            if (request.getOperator() != null && !request.getOperator().isEmpty()) {
                predicates.add(cb.equal(root.get("operator").get("name"), request.getOperator()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Execute Query
        Page<Route> routePage = routeRepository.findAll(spec, pageable);

        // Map Entities to RouteResponse List
        List<RouteResponse> routeList = routePage.getContent().stream()
                .map(this::mapToDto)
                .toList();

        // Build PaginationDto
        PaginationDto pagination = PaginationDto.builder()
                .total((int) routePage.getTotalElements())
                .limit(routePage.getSize())
                .page(routePage.getNumber())
                .totalPages(routePage.getTotalPages())
                .build();

        // Return the Combined Response
        return RouteSearchResponse.builder()
                .routes(routeList)
                .pagination(pagination)
                .build();
    }

    private RouteResponse.StopDto mapToStopDto(RouteStop stop) {
        return RouteResponse.StopDto.builder()
                .id(stop.getId())
                .name(stop.getStation().getName())
                .address(stop.getFullAddress())
                .duration(stop.getDuration())
                .build();
    }
}