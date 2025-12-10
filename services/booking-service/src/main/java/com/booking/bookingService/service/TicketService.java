package com.booking.bookingService.service;

import com.booking.bookingService.dto.*;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final RedisLockService redisLockService;
    private final TripSeatRepository seatStatusRepository;
    private final TripRepository tripRepository;
    private final TicketRepository ticketRepository;
    // private final SeatRepository seatRepository;

    private static final long LOCK_TIMEOUT_SECONDS = 600;
    // =================================================================
    // PHASE 1: LOCK / UNLOCK SEATS (Real-time interactions)
    // =================================================================

    @Transactional
    public void lockSeats(SeatLockRequest request, String userEmail) {
        // Xác định ai là người giữ lock (User login hoặc Guest session)
        String lockOwnerId = determineOwnerId(userEmail, request.getSessionId());

        // 1. Validate Trip
        if (!tripRepository.existsById(request.getTripId())) {
            throw new ResourceNotFoundException("Trip does not exist");
        }

        List<String> successfullyLockedKeys = new ArrayList<>();

        try {
            // 2. Redis Locking Process
            for (String seatCode : request.getSeats()) {
                String key = generateSeatLockKey(request.getTripId(), seatCode);
                
                // Cố gắng lock
                boolean acquired = redisLockService.tryLock(key, lockOwnerId, LOCK_TIMEOUT_SECONDS);
                
                if (!acquired) {
                    // Nếu không lock được, kiểm tra xem có phải chính mình đang lock không (trường hợp F5 lại)
                    String currentOwner = redisLockService.getLockOwner(key);
                    if (!lockOwnerId.equals(currentOwner)) {
                        throw new IllegalStateException("Seat " + seatCode + " is being held by another user.");
                    }
                    // Nếu là chính mình, gia hạn thêm thời gian
                    redisLockService.refreshLock(key, LOCK_TIMEOUT_SECONDS);
                }
                successfullyLockedKeys.add(key);
            }

            // 3. Update Database Status -> LOCKED
            updateSeatStatusInDb(request.getTripId(), request.getSeats(), TripSeat.Status.LOCKED);

        } catch (Exception e) {
            // Rollback: Nếu lỗi, nhả các ghế đã lỡ lock trong Redis
            for (String key : successfullyLockedKeys) {
                // Chỉ unlock nếu mình là owner (an toàn)
                if (lockOwnerId.equals(redisLockService.getLockOwner(key))) {
                    redisLockService.unlock(key);
                }
            }
            throw e; // Ném lỗi ra để Controller bắt
        }
    }

    @Transactional
    public void unlockSeats(SeatLockRequest request, String userEmail) {
        String lockOwnerId = determineOwnerId(userEmail, request.getSessionId());

        for (String seatCode : request.getSeats()) {
            String key = generateSeatLockKey(request.getTripId(), seatCode);
            String currentOwner = redisLockService.getLockOwner(key);

            // Chỉ cho phép unlock nếu đúng là chính chủ
            if (lockOwnerId.equals(currentOwner)) {
                redisLockService.unlock(key);
            }
        }

        // Update Database Status -> AVAILABLE (Chỉ revert nếu chưa bán)
        revertSeatStatusToAvailable(request.getTripId(), request.getSeats());
    }

    // =================================================================
    // PHASE 2: CREATE TICKET (Finalize Booking)
    // =================================================================
    @Transactional
    public TicketResponse createTicket(TicketRequest request, String userEmail) {
        String lockOwnerId = determineOwnerId(userEmail, request.getSessionId());

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        // 1. SECURITY CHECK: User có thực sự đang giữ ghế không?
        validateLockOwnership(trip.getId(), request.getSeats(), lockOwnerId);

        if (trip.getAvailableSeats() < request.getSeats().size()) {
            throw new IllegalStateException("Not enough seats available");
        }

        // Cập nhật số lượng ghế trống của Trip
        trip.setAvailableSeats(trip.getAvailableSeats() - request.getSeats().size());
        tripRepository.save(trip); // Lưu Trip đã update

        // 2. Tính toán tiền
        BigDecimal pricePerTicket = trip.getPrice();
        BigDecimal total = pricePerTicket.multiply(BigDecimal.valueOf(request.getSeats().size()));

        // 3. Tạo Ticket
        Ticket ticket = Ticket.builder()
                .ticketCode("TK" + System.currentTimeMillis())
                .userEmail(userEmail) // Có thể null
                .trip(trip)
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .totalAmount(total)
                .status(Ticket.TicketStatus.PENDING)
                .createdAt(LocalDateTime.now())
                // Gia hạn lock thêm 10 phút để thanh toán
                .lockedUntil(LocalDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS))
                .seats(request.getSeats())
                .build();

        ticketRepository.save(ticket);

        // 4. Quan trọng: Refresh Redis Lock để user có thời gian thanh toán
        refreshLocks(trip.getId(), request.getSeats(), LOCK_TIMEOUT_SECONDS);

        return TicketResponse.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .tripId(trip.getId())
                .status("pending")
                .seats(ticket.getSeats())
                .passengers(ticket.getSeats().size())
                .pricing(TicketResponse.PricingDto.builder()
                        .total(total)
                        .currency("VND")
                        .build())
                .lockedUntil(ticket.getLockedUntil())
                .createdAt(ticket.getCreatedAt())
                .build();
    }

    // =================================================================
    // UTILITIES & HELPERS
    // =================================================================
    
    // API này dùng để lấy danh sách ghế hiển thị lên UI
    // Nó bao gồm logic "Lazy Sync": Nếu Redis hết hạn mà DB vẫn Locked -> Reset về Available
    public List<TripSeat> getTripSeatsAndSync(UUID tripId) {
        List<TripSeat> seats = seatStatusRepository.findByTripId(tripId);
        List<TripSeat> seatsToUpdate = new ArrayList<>();

        for (TripSeat seat : seats) {
            if (seat.getStatus() == TripSeat.Status.LOCKED) {
                String key = generateSeatLockKey(tripId, seat.getSeat().getSeatCode());
                // Kiểm tra Redis, nếu key không còn tồn tại -> Lock đã hết hạn
                if (redisLockService.getLockOwner(key) == null) {
                    seat.setStatus(TripSeat.Status.AVAILABLE);
                    seatsToUpdate.add(seat);
                }
            }
        }

        if (!seatsToUpdate.isEmpty()) {
            seatStatusRepository.saveAll(seatsToUpdate);
        }
        return seats;
    }

    private void updateSeatStatusInDb(UUID tripId, List<String> seatCodes, TripSeat.Status status) {
        List<TripSeat> allStatuses = seatStatusRepository.findByTripId(tripId);
        List<TripSeat> targetStatuses = allStatuses.stream()
                .filter(s -> seatCodes.contains(s.getSeat().getSeatCode()))
                .collect(Collectors.toList());

        if (targetStatuses.size() != seatCodes.size()) {
            throw new ResourceNotFoundException("Some seats are invalid");
        }

        for (TripSeat ts : targetStatuses) {
            if (ts.getStatus() == TripSeat.Status.BOOKED) {
                throw new IllegalStateException("Seat " + ts.getSeat().getSeatCode() + " has already been sold.");
            }
            ts.setStatus(status);
        }
        seatStatusRepository.saveAll(targetStatuses);
    }

    private void revertSeatStatusToAvailable(UUID tripId, List<String> seatCodes) {
        List<TripSeat> statuses = seatStatusRepository.findByTripIdAndSeat_SeatCodeIn(tripId, seatCodes);
        statuses.forEach(s -> {
            // Chỉ revert về AVAILABLE nếu nó chưa bị BOOKED (tránh lỗi logic)
            if (s.getStatus() != TripSeat.Status.BOOKED) {
                s.setStatus(TripSeat.Status.AVAILABLE);
            }
        });
        seatStatusRepository.saveAll(statuses);
    }

    private void validateLockOwnership(UUID tripId, List<String> seatCodes, String ownerId) {
        for (String seatCode : seatCodes) {
            String key = generateSeatLockKey(tripId, seatCode);
            String currentOwner = redisLockService.getLockOwner(key);

            if (currentOwner == null) {
                throw new IllegalStateException("Seat " + seatCode + " lock has expired. Please select again.");
            }
            if (!currentOwner.equals(ownerId)) {
                throw new IllegalStateException("Seat " + seatCode + " has been taken by another user.");
            }
        }
    }

    private void refreshLocks(UUID tripId, List<String> seatCodes, long timeout) {
        for (String seatCode : seatCodes) {
            String key = generateSeatLockKey(tripId, seatCode);
            redisLockService.refreshLock(key, timeout);
        }
    }

    private String generateSeatLockKey(UUID tripId, String seatCode) {
        return "lock:seat:" + tripId + ":" + seatCode;
    }

    private String determineOwnerId(String userEmail, String sessionId) {
        if (userEmail != null && !userEmail.isEmpty()) {
            return userEmail;
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        throw new IllegalArgumentException("User Email or Session ID is required");
    }

    public TicketDetailResponse getTicketDetail(UUID ticketId, String userEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        // Security check if needed
        if (userEmail != null && !userEmail.equals(ticket.getUserEmail())) {
             // throw exception
            throw new ResourceNotFoundException("Ticket not found");
        }
        
        // Lazy Check Expired
        if (ticket.getStatus() == Ticket.TicketStatus.PENDING && 
            ticket.getLockedUntil().isBefore(LocalDateTime.now())) {
            expireTicketNow(ticket);
        }

        return mapToDetailResponse(ticket);
    }

    @Transactional
    public TicketCancelResponse cancelTicket(UUID ticketId, CancelTicketRequest request, String userEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (ticket.getStatus() == Ticket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Ticket is already cancelled");
        }

        if (ticket.getTrip().getDepartureTime().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Cannot cancel ticket close to departure time");
        }

        releaseSeats(ticket.getTrip().getId(), ticket.getSeats());

        Trip trip = ticket.getTrip();
        trip.setAvailableSeats(trip.getAvailableSeats() + ticket.getSeats().size());
        tripRepository.save(trip);

        ticket.setStatus(Ticket.TicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        BigDecimal refundAmount = BigDecimal.ZERO;
        if (request.isRequestRefund()) {
            refundAmount = ticket.getTotalAmount().multiply(BigDecimal.valueOf(0.8));
        }

        return TicketCancelResponse.builder()
                .ticketId(ticket.getId())
                .status("cancelled")
                .cancelledAt(ticket.getCancelledAt())
                .refund(TicketCancelResponse.RefundDto.builder()
                        .amount(refundAmount)
                        .percentage(80)
                        .processingTime("3-5 business days")
                        .refundMethod("original payment method")
                        .build())
                .build();
    }

    public Page<TicketHistoryResponse> getUserTickets(String userEmail, String statusStr, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Ticket.TicketStatus statusTemp = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                statusTemp = Ticket.TicketStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) { /* ignore */ }
        }
        final Ticket.TicketStatus status = statusTemp;

        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userEmail"), userEmail));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (fromDateTime != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime));
            if (toDateTime != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDateTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ticketRepository.findAll(spec, pageable).map(this::mapToHistoryResponse);
    }

    private void releaseSeats(UUID tripId, List<String> seatCodes) {
        for (String seatCode : seatCodes) {
            redisLockService.unlock("lock:seat:" + tripId + ":" + seatCode);
        }
        List<TripSeat> statuses = seatStatusRepository.findByTripIdAndSeat_SeatCodeIn(tripId, seatCodes);
        statuses.forEach(s -> s.setStatus(TripSeat.Status.AVAILABLE));
        seatStatusRepository.saveAll(statuses);
    }
    
    private void expireTicketNow(Ticket ticket) {
        ticket.setStatus(Ticket.TicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());
        releaseSeats(ticket.getTrip().getId(), ticket.getSeats());
        ticketRepository.save(ticket);
    }

    private TicketDetailResponse mapToDetailResponse(Ticket ticket) {
        return TicketDetailResponse.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .userEmail(ticket.getUserEmail())

                .contactName(ticket.getContactName())
                .contactEmail(ticket.getContactEmail())
                .contactPhone(ticket.getContactPhone())

                .status(ticket.getStatus().name().toLowerCase())
                .createdAt(ticket.getCreatedAt())
                .confirmedAt(ticket.getConfirmedAt())
                .seats(ticket.getSeats())
                .tripDetails(TicketDetailResponse.TripDetailsDto.builder()
                        .tripId(ticket.getTrip().getId())
                        .route(ticket.getTrip().getRoute().getOrigin() + " → " + ticket.getTrip().getRoute().getDestination())
                        .operator(ticket.getTrip().getOperator().getName())
                        .departureTime(ticket.getTrip().getDepartureTime())
                        .arrivalTime(ticket.getTrip().getArrivalTime())
                        .build())
                .pricing(TicketDetailResponse.PricingDto.builder()
                        .total(ticket.getTotalAmount())
                        .currency("VND")
                        .build())
                .build();
    }

    private TicketHistoryResponse mapToHistoryResponse(Ticket ticket) {
        return TicketHistoryResponse.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .totalAmount(ticket.getTotalAmount())
                .status(ticket.getStatus().name().toLowerCase())
                .createdAt(ticket.getCreatedAt())
                .seats(ticket.getSeats())
                .trip(TicketHistoryResponse.TripSummaryDto.builder()
                        .route(ticket.getTrip().getRoute().getOrigin() + " → " + ticket.getTrip().getRoute().getDestination())
                        .departureTime(ticket.getTrip().getDepartureTime())
                        .operator(ticket.getTrip().getOperator().getName())
                        .build())
                .build();
    }
}