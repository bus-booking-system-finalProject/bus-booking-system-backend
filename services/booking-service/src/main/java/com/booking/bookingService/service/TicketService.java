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
    private final SeatStatusRepository seatStatusRepository;
    private final TripRepository tripRepository;
    private final TicketRepository ticketRepository;
    // private final SeatRepository seatRepository;

    private static final long LOCK_TIMEOUT_SECONDS = 600;

    @Transactional
    public TicketResponse createTicket(TicketRequest request, String userEmail) {
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        String userIdForLock = userEmail != null ? userEmail : "GUEST-" + UUID.randomUUID();
        holdSeatsInternal(trip.getId(), request.getSeats(), userIdForLock);

        BigDecimal pricePerTicket = trip.getPrice();
        BigDecimal subtotal = pricePerTicket.multiply(BigDecimal.valueOf(request.getSeats().size()));
        BigDecimal serviceFee = BigDecimal.valueOf(20000);
        BigDecimal total = subtotal.add(serviceFee);

        Ticket ticket = Ticket.builder()
                .ticketCode("TK" + System.currentTimeMillis())
                .userId(userEmail)
                .trip(trip)
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .totalAmount(total)
                .status(Ticket.TicketStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .lockedUntil(LocalDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS))
                .seats(request.getSeats())
                .build();

        ticketRepository.save(ticket);

        return TicketResponse.builder()
                .ticketId(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .tripId(trip.getId())
                .status("pending")
                .seats(ticket.getSeats())
                .passengers(ticket.getSeats().size())
                .pricing(TicketResponse.PricingDto.builder()
                        .subtotal(subtotal)
                        .serviceFee(serviceFee)
                        .total(total)
                        .currency("VND")
                        .build())
                .lockedUntil(ticket.getLockedUntil())
                .createdAt(ticket.getCreatedAt())
                .build();
    }

    public TicketDetailResponse getTicketDetail(UUID ticketId, String userEmail) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        // Security check if needed
        if (userEmail != null && !userEmail.equals(ticket.getUserId())) {
             // throw exception
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
            predicates.add(cb.equal(root.get("userId"), userEmail));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (fromDateTime != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime));
            if (toDateTime != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDateTime));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ticketRepository.findAll(spec, pageable).map(this::mapToHistoryResponse);
    }

    // --- Helpers ---
    private void holdSeatsInternal(UUID tripId, List<String> seatCodes, String userId) {
        List<String> lockedKeys = new ArrayList<>();
        try {
            for (String seatCode : seatCodes) {
                String key = "lock:seat:" + tripId + ":" + seatCode;
                boolean acquired = redisLockService.tryLock(key, userId, LOCK_TIMEOUT_SECONDS);
                if (!acquired) throw new IllegalStateException("Ghế " + seatCode + " đang được người khác chọn.");
                lockedKeys.add(key);
            }

            List<SeatStatus> allStatuses = seatStatusRepository.findByTripId(tripId);
            List<SeatStatus> targetStatuses = allStatuses.stream()
                    .filter(s -> seatCodes.contains(s.getSeat().getSeatCode()))
                    .collect(Collectors.toList());

            if (targetStatuses.size() != seatCodes.size()) throw new ResourceNotFoundException("Ghế không hợp lệ");

            for (SeatStatus status : targetStatuses) {
                if (status.getState() == SeatStatus.SeatState.BOOKED) {
                    throw new IllegalStateException("Ghế " + status.getSeat().getSeatCode() + " đã được bán.");
                }
                status.setState(SeatStatus.SeatState.LOCKED);
            }
            seatStatusRepository.saveAll(targetStatuses);
        } catch (Exception e) {
            for (String key : lockedKeys) redisLockService.unlock(key);
            throw e;
        }
    }

    private void releaseSeats(UUID tripId, List<String> seatCodes) {
        for (String seatCode : seatCodes) {
            redisLockService.unlock("lock:seat:" + tripId + ":" + seatCode);
        }
        List<SeatStatus> statuses = seatStatusRepository.findByTripIdAndSeat_SeatCodeIn(tripId, seatCodes);
        statuses.forEach(s -> s.setState(SeatStatus.SeatState.AVAILABLE));
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
                .userId(ticket.getUserId())

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