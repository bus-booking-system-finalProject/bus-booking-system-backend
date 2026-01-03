package com.booking.bookingService.service;

import com.booking.bookingService.dto.station.StationRequest;
import com.booking.bookingService.dto.station.StationResponse;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Station;
import com.booking.bookingService.repository.OperatorRepository;
import com.booking.bookingService.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final OperatorRepository operatorRepository;

    @Transactional
    public StationResponse createStation(StationRequest request, UUID currentOperatorId) {
        Operator operator = operatorRepository.findById(currentOperatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));

        Station station = Station.builder()
                .name(request.getName())
                .address(request.getAddress())
                .ward(request.getWard())
                .city(request.getCity())
                .operator(operator)
                .build();

        return mapToResponse(stationRepository.save(station));
    }

    public List<StationResponse> getAllStations(UUID currentOperatorId) {
        return stationRepository.findAllByOperatorId(currentOperatorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public StationResponse getStation(UUID stationId, UUID currentOperatorId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        validateOwnership(station, currentOperatorId);

        return mapToResponse(station);
    }

    @Transactional
    public StationResponse updateStation(UUID stationId, StationRequest request, UUID currentOperatorId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        // 1. Security Check
        validateOwnership(station, currentOperatorId);

        // 2. Update fields
        station.setName(request.getName());
        station.setAddress(request.getAddress());
        station.setWard(request.getWard());
        station.setCity(request.getCity());

        // Note: We DO NOT update the operator here.
        // A station cannot change ownership from FUTA to Kumho via this API.

        return mapToResponse(stationRepository.save(station));
    }

    @Transactional
    public void deleteStation(UUID stationId, UUID currentOperatorId) {
        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        validateOwnership(station, currentOperatorId);

        stationRepository.delete(station);
    }

    private StationResponse mapToResponse(Station station) {
        return StationResponse.builder()
                .id(station.getId())
                .name(station.getName())
                .address(station.getAddress())
                .ward(station.getWard())
                .city(station.getCity())
                .build();
    }

    private void validateOwnership(Station station, UUID currentOperatorId) {
        if (station.getOperator() == null || !station.getOperator().getId().equals(currentOperatorId)) {
            // Throw 403 Forbidden if they try to touch another operator's data
            throw new RuntimeException("Access Denied: You do not own this station");
        }
    }

    // Fulltext search for stations (public - all stations)
    public List<StationResponse> searchStations(String keyword) {
        return stationRepository.searchByKeyword(keyword).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Fulltext search for operator's stations
    public List<StationResponse> searchOperatorStations(String keyword, UUID operatorId) {
        return stationRepository.searchByKeywordAndOperatorId(keyword, operatorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Search stations by city
    public List<StationResponse> searchByCity(String city) {
        return stationRepository.searchByCity(city).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}