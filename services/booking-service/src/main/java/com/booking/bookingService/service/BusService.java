package com.booking.bookingService.service;

import com.booking.bookingService.dto.bus.BusRequest;
import com.booking.bookingService.dto.ticket.SeatDefinition;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Bus;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Seat;
import com.booking.bookingService.repository.BusRepository;
import com.booking.bookingService.repository.OperatorRepository;
import com.booking.bookingService.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BusService {
    private final BusRepository busRepository;
    private final OperatorRepository operatorRepository;
    private final SeatRepository seatRepository;

    public Bus createBus(BusRequest request) {
        Operator operator = operatorRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        Bus bus = Bus.builder()
                .operator(operator)
                .plateNumber(request.getPlateNumber())
                .model(request.getModel())
                .type(request.getType())
                .seatCapacity(request.getSeatCapacity())
                .build();
        return busRepository.save(bus);
    }

    public Bus getBus(UUID id) {
        return busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
    }
    
    public List<Bus> getAllBuses() {
        return busRepository.findAll();
    }

    public Bus updateBus(UUID id, BusRequest request) {
        Bus bus = getBus(id);
        if (!bus.getOperator().getId().equals(request.getOperatorId())) {
            Operator operator = operatorRepository.findById(request.getOperatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
            bus.setOperator(operator);
        }
        bus.setPlateNumber(request.getPlateNumber());
        bus.setModel(request.getModel());
        bus.setType(request.getType());
        bus.setSeatCapacity(request.getSeatCapacity());
        return busRepository.save(bus);
    }

    public void deleteBus(UUID id) {
        if (!busRepository.existsById(id)) throw new ResourceNotFoundException("Bus not found");
        busRepository.deleteById(id);
    }

    /**
     * SAVES A FULLY CUSTOM SEAT MAP
     */
    @Transactional
    public List<Seat> saveCustomSeatMap(UUID busId, List<SeatDefinition> seatDefinitions) {
        Bus bus = getBus(busId);

        // Clear existing seats
        List<Seat> existingSeats = seatRepository.findByBusId(busId);
        seatRepository.deleteAll(existingSeats);

        // Map DTOs to Entities
        List<Seat> newSeats = seatDefinitions.stream().map(def -> {
            return Seat.builder()
                .bus(bus)
                .seatCode(def.getSeatCode())
                .gridRow(def.getRow())
                .gridCol(def.getCol())
                .deckNumber(def.getDeck())
                .build();
        }).collect(Collectors.toList());

        bus.setSeatCapacity(newSeats.size());
        busRepository.save(bus);

        return seatRepository.saveAll(newSeats);
    }
}