package com.booking.bookingService.service;

import com.booking.bookingService.Enum.BusType;
import com.booking.bookingService.dto.bus.BusDetailsResponse;
import com.booking.bookingService.dto.bus.BusRequest;
import com.booking.bookingService.dto.bus.BusResponse;
import com.booking.bookingService.dto.bus_model.BusModelDetailsResponse;
import com.booking.bookingService.dto.bus_model.BusModelRequest;
import com.booking.bookingService.dto.bus_model.BusModelResponse;
import com.booking.bookingService.dto.bus_model.SeatDto;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.Bus;
import com.booking.bookingService.model.BusModel;
import com.booking.bookingService.model.Operator;
import com.booking.bookingService.model.Seat;
import com.booking.bookingService.repository.BusModelRepository;
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
    private final BusModelRepository busModelRepository;
    private final OperatorRepository operatorRepository;
    private final SeatRepository seatRepository;

    // --- 1. BUS MODEL LOGIC (Templates) ---

    @Transactional
    public BusModelResponse createBusModel(BusModelRequest request, UUID operatorId) {
        Operator operator = operatorRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));

        // VALIDATION: Seat Map is MANDATORY
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new IllegalArgumentException("A Seat Map (seatDefinitions) is required when creating a new Bus Model.");
        }

        // Convert String "SLEEPER" -> Enum BusType.SLEEPER
        BusType typeEnum;
        try {
            typeEnum = BusType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            typeEnum = BusType.SLEEPER; // Default fallback or throw error
        }

        BusModel busModel = BusModel.builder()
                .operator(operator)
                .name(request.getName())
                .type(typeEnum)
                .totalDecks(request.getTotalDecks())
                .gridRows(request.getGridRows())
                .gridColumns(request.getGridColumns())
                .isLimousine(Boolean.TRUE.equals(request.getIsLimousine()))
                .hasWC(Boolean.TRUE.equals(request.getHasWC()))
                .build();

        BusModel savedModel = busModelRepository.save(busModel);

        // Save Seats for this Model (Guaranteed to run because of check above)
        saveSeatMapForModel(savedModel.getId(), request.getSeats(), operatorId);
        
        return mapToResponse(savedModel);
    }

    @Transactional
    public BusModelResponse updateBusModel(UUID busModelId, BusModelRequest request, UUID currentOperatorId) {
        // 1. Find the existing model
        BusModel busModel = busModelRepository.findById(busModelId)
                .orElseThrow(() -> new ResourceNotFoundException("BusModel not found"));

        // 2. Validate Ownership: Ensure the operator trying to update owns this model
        if (!busModel.getOperator().getId().equals(currentOperatorId)) {
            throw new RuntimeException("Access Denied: You do not own this Bus Model");
        }

        BusType typeEnum;
        try {
            typeEnum = BusType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            typeEnum = BusType.SLEEPER; // Default fallback or throw error
        }

        // 3. Update basic metadata fields
        busModel.setName(request.getName());
        busModel.setType(typeEnum);
        busModel.setTotalDecks(request.getTotalDecks());
        busModel.setGridRows(request.getGridRows());
        busModel.setGridColumns(request.getGridColumns()); // Mapping gridColumn from request to gridColumns in entity
        busModel.setLimousine(request.getIsLimousine());
        busModel.setHasWC(request.getHasWC());

        // 4. Synchronize the Seat Map
        // We reuse the logic: Clear old seats and insert new ones based on the request definitions
        if (request.getSeats() != null && !request.getSeats().isEmpty()) {
            // Clear existing seats linked to this model
            List<Seat> existingSeats = seatRepository.findByBusModelId(busModelId);
            seatRepository.deleteAll(existingSeats);

            // Map definitions to new Seat entities
            List<Seat> newSeats = request.getSeats().stream().map(def -> Seat.builder()
                    .busModel(busModel)
                    .seatCode(def.getCode())
                    .gridRow(def.getRow())
                    .gridCol(def.getCol())
                    .deckNumber(def.getDeck())
                    .build()).collect(Collectors.toList());

            // Update the cached capacity based on the new seat list
            busModel.setSeatCapacity(newSeats.size());
            
            // Save the new seats
            seatRepository.saveAll(newSeats);
        }

        // 5. Save and return the updated model
        BusModel savedModel = busModelRepository.save(busModel);

        return mapToResponse(savedModel);
    }

    @Transactional
    public List<Seat> saveSeatMapForModel(UUID busModelId, List<SeatDto> seats, UUID operatorId) {
        BusModel busModel = busModelRepository.findById(busModelId)
                .orElseThrow(() -> new ResourceNotFoundException("BusModel not found"));

        if (!busModel.getOperator().getId().equals(operatorId)) 
            throw new RuntimeException("Access Denied: You do not own this Bus Model");

        // 1. Clear existing template seats for this model (Supports Update Logic)
        List<Seat> existing = seatRepository.findByBusModelId(busModelId);
        seatRepository.deleteAll(existing);

        // 2. Create new seats linked to BusModel
        List<Seat> newSeats = seats.stream().map(def -> Seat.builder()
                .busModel(busModel) // Link to Model
                .seatCode(def.getCode())
                .gridRow(def.getRow())
                .gridCol(def.getCol())
                .deckNumber(def.getDeck())
                .build()).collect(Collectors.toList());

        // 3. Update cached capacity
        busModel.setSeatCapacity(newSeats.size());
        busModelRepository.save(busModel);
        
        return seatRepository.saveAll(newSeats);
    }

    public List<BusModelResponse> getMyBusModels(UUID operatorId) {
        return busModelRepository.findAllByOperatorId(operatorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public BusModelDetailsResponse getBusModelDetails(UUID busModelId, UUID operatorId) {
        // 1. Retrieve the BusModel or throw exception if not found
        BusModel busModel = busModelRepository.findById(busModelId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus Model not found with ID: " + busModelId));
        
        if (!busModel.getOperator().getId().equals(operatorId)) 
            throw new RuntimeException("Access Denied: You do not own this Bus Model");

        // 2. Map the BusModel to BusModelResponse DTO
        

        return mapToBusModelDetailsResponse(busModel);
    }

    // --- 2. PHYSICAL BUS LOGIC ---

    @Transactional
    public BusResponse createBus(BusRequest request, UUID currentOperatorId) {
        Operator operator = operatorRepository.findById(currentOperatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator account not found"));

        // Look up the Model (Template)
        BusModel model = busModelRepository.findById(request.getBusModelId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus Model not found"));

        // Verify Model belongs to Operator
        if (!model.getOperator().getId().equals(currentOperatorId)) {
            throw new RuntimeException("Cannot use a Bus Model that belongs to another operator");
        }

        Bus bus = Bus.builder()
                .operator(operator)
                .model(model)
                .plateNumber(request.getPlateNumber())
                .isActive(request.getIsActive())
                .build();
        
        Bus savedBus = busRepository.save(bus);
        
        return mapToResponse(savedBus);
    }

    
    public List<BusResponse> getAllBuses(UUID currentOperatorId) {
        return busRepository.findAllByOperatorId(currentOperatorId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BusResponse updateBus(UUID id, BusRequest request, UUID currentOperatorId) {
        Bus bus = getBus(id, currentOperatorId);
        
        BusModel model = busModelRepository.findById(request.getBusModelId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus Model not found"));

        bus.setPlateNumber(request.getPlateNumber());
        bus.setModel(model);
        bus.setActive(request.getIsActive());
        
        Bus savedBus = busRepository.save(bus);

        return mapToResponse(savedBus);
    }

    @Transactional
    public void deleteBus(UUID id, UUID currentOperatorId) {
        Bus bus = getBus(id, currentOperatorId);
        busRepository.delete(bus);
    }

    public Bus getBus(UUID id, UUID currentOperatorId) {
        Bus bus = busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found"));
        validateOwnership(bus, currentOperatorId);
        
        return bus;
    }

    public BusDetailsResponse getBusDetails(UUID busId, UUID currentOperatorId) {
        Bus bus = getBus(busId, currentOperatorId);

        validateOwnership(bus, currentOperatorId);

        return BusDetailsResponse.builder()
                .id(bus.getId())
                .model(mapToBusModelDetailsResponse(bus.getModel()))
                .plateNumber(bus.getPlateNumber())
                .isActive(bus.isActive())
                .build();
    }

    private BusModelDetailsResponse mapToBusModelDetailsResponse(BusModel busModel) {
        List<SeatDto> seatDtos = busModel.getSeats().stream()
                .map(seat -> SeatDto.builder()
                        .code(seat.getSeatCode())
                        .row(seat.getGridRow())
                        .col(seat.getGridCol())
                        .deck(seat.getDeckNumber())
                        .build())
                .collect(Collectors.toList());
                
        BusModelResponse modelDto = mapToResponse(busModel);
        
        return BusModelDetailsResponse.builder()
                .details(modelDto)
                .totalDecks(busModel.getTotalDecks())
                .gridRows(busModel.getGridRows())
                .gridColumns(busModel.getGridColumns())
                .seats(seatDtos)
                .build();
    }

    // Helper Mapper
    private BusResponse mapToResponse(Bus bus) {
        return BusResponse.builder()
                .id(bus.getId())
                .model(mapToResponse(bus.getModel()))
                .plateNumber(bus.getPlateNumber())
                .isActive(bus.isActive())
                .build();
    }

    // Helper Mapper
    private BusModelResponse mapToResponse(BusModel busModel) {
        return BusModelResponse.builder()
                .id(busModel.getId())
                .name(busModel.getName())
                .typeDisplay(busModel.getTypeDisplay()) 
                .type(busModel.getType().toString())
                .isLimousine(busModel.isLimousine())
                .hasWC(busModel.isHasWC())
                .seatCapacity(busModel.getSeatCapacity())
                .build();
    }

    // --- Security Helper ---
    private void validateOwnership(Bus bus, UUID currentOperatorId) {
        if (!bus.getOperator().getId().equals(currentOperatorId)) {
            throw new RuntimeException("Access Denied: You do not own this bus");
        }
    }
}