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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationRepository stationRepository;
    private final OperatorRepository operatorRepository;

    public StationResponse createStation(StationRequest request) {
        Operator operator = null;
        if (request.getOperatorId() != null) {
            operator = operatorRepository.findById(request.getOperatorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
        }

        Station station = Station.builder()
                .name(request.getName())
                .address(request.getAddress())
                .ward(request.getWard())
                .city(request.getCity())
                .operator(operator)
                .build();

        return mapToResponse(stationRepository.save(station));
    }

    public List<StationResponse> getAllStations() {
        return stationRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public StationResponse getStation(UUID id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
        return mapToResponse(station);
    }

    public StationResponse updateStation(UUID id, StationRequest request) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        station.setName(request.getName());
        station.setAddress(request.getAddress());
        station.setWard(request.getWard());
        station.setCity(request.getCity());

        if (request.getOperatorId() != null) {
            Operator operator = operatorRepository.findById(request.getOperatorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
            station.setOperator(operator);
        }

        return mapToResponse(stationRepository.save(station));
    }

    public void deleteStation(UUID id) {
        if (!stationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Station not found");
        }
        stationRepository.deleteById(id);
    }

    private StationResponse mapToResponse(Station station) {
        return StationResponse.builder()
                .id(station.getId())
                .name(station.getName())
                .address(station.getAddress())
                .ward(station.getWard())
                .city(station.getCity())
                .operatorId(station.getOperator() != null ? station.getOperator().getId() : null)
                .operatorName(station.getOperator() != null ? station.getOperator().getName() : null)
                .build();
    }
}