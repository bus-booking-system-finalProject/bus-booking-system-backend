package com.booking.bookingService.service;

import com.booking.bookingService.dto.ticket.CancelTicketRequest;
import com.booking.bookingService.dto.ticket.GuestLookupRequest;
import com.booking.bookingService.dto.ticket.SeatLockRequest;
import com.booking.bookingService.dto.ticket.TicketCancelResponse;
import com.booking.bookingService.dto.ticket.TicketDetailResponse;
import com.booking.bookingService.dto.ticket.TicketHistoryResponse;
import com.booking.bookingService.dto.ticket.TicketLookupResponse;
import com.booking.bookingService.dto.ticket.TicketRequest;
import com.booking.bookingService.dto.ticket.TicketResponse;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import jakarta.persistence.criteria.Predicate;
import jakarta.ws.rs.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final SocketIOService socketIOService;
    private final TripSeatRepository seatStatusRepository;
    private final TripRepository tripRepository;
    private final TicketRepository ticketRepository;
    private final RouteStopRepository routeStopRepository;
    // private final SeatRepository seatRepository;

    private static final long LOCK_TIMEOUT_SECONDS = 600;
    // =================================================================
    // PHASE 1: LOCK / UNLOCK SEATS (Real-time interactions)
    // =================================================================

    @Transactional
    public void lockSeats(SeatLockRequest request) {
        // Xác định ai là người giữ lock (User login hoặc Guest session)
        String lockOwnerId = determineOwnerId(request.getSessionId());

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
                    // Nếu không lock được, kiểm tra xem có phải chính mình đang lock không (trường
                    // hợp F5 lại)
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

            // Đăng ký gửi socket sau khi commit thành công
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    socketIOService.broadcastSeatUpdate(request.getTripId(), request.getSeats(), "locked");
                }
            });

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
    public void unlockSeats(SeatLockRequest request) {
        String lockOwnerId = determineOwnerId(request.getSessionId());

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

        // Broadcast that seats are now AVAILABLE
        socketIOService.broadcastSeatUpdate(request.getTripId(), request.getSeats(), "available");
    }

    // =================================================================
    // PHASE 2: CREATE TICKET (Finalize Booking)
    // =================================================================
    @Transactional(rollbackFor = Exception.class)
    public TicketResponse createTicket(TicketRequest request, String userEmail) {
        String lockOwnerId = determineOwnerId(request.getSessionId());

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        // 1. SECURITY CHECK: User có thực sự đang giữ ghế không?
        validateLockOwnership(trip.getId(), request.getSeats(), lockOwnerId);

        // Fetch Selected Stops
        RouteStop pickupStop = routeStopRepository.findById(request.getPickupId())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid Pickup ID"));
        RouteStop dropoffStop = routeStopRepository.findById(request.getDropoffId())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid Dropoff ID"));

        // Validate stops belong to this trip
        if (!pickupStop.getRoute().getId().equals(trip.getRoute().getId()) ||
                !dropoffStop.getRoute().getId().equals(trip.getRoute().getId())) {
            throw new IllegalArgumentException("Selected stops do not belong to this trip's route");
        }

        int updatedRows = tripRepository.decrementAvailableSeats(trip.getId(), request.getSeats().size());
        if (updatedRows == 0)
            throw new IllegalStateException("Not enough seats available");
        trip.setAvailableSeats(trip.getAvailableSeats() - request.getSeats().size());

        // Cập nhật lại object trip trong memory để hiển thị đúng (dù DB đã trừ rồi)
        trip.setAvailableSeats(trip.getAvailableSeats() - request.getSeats().size());

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
                .pickupRouteStop(pickupStop)
                .dropoffRouteStop(dropoffStop)
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
    // Nó bao gồm logic "Lazy Sync": Nếu Redis hết hạn mà DB vẫn Locked -> Reset về
    // Available
    @Transactional
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

    private String determineOwnerId(String sessionId) {
        // Chỉ dùng Session ID để làm key lock (bất kể user đã login hay chưa)
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        throw new IllegalArgumentException("Session ID is required");
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
        // 1. Lấy thông tin vé
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        // 2. [SECURITY] Kiểm tra quyền sở hữu vé
        // Giả sử Ticket entity có lưu userEmail hoặc userId
        if (!ticket.getUserEmail().equals(userEmail)) {
            throw new AccessDeniedException("You are not authorized to cancel this ticket");
        }

        // 3. Validate trạng thái
        if (ticket.getStatus() == Ticket.TicketStatus.CANCELLED) {
            throw new IllegalStateException("Ticket is already cancelled");
        }

        // 4. Validate thời gian (Magic number '2' nên đưa vào Config/Constant)
        if (ticket.getTrip().getDepartureTime().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Cannot cancel ticket close to departure time");
        }

        // 5. Xử lý logic vé
        // Release ghế cụ thể (bảng SeatAllocation hoặc tương tự)
        releaseSeats(ticket.getTrip().getId(), ticket.getSeats());

        // [CONCURRENCY FIX] Update số lượng ghế trực tiếp trong DB để tránh Race
        // Condition
        // Thay vì get/set, hãy viết hàm custom trong Repository
        tripRepository.incrementAvailableSeats(ticket.getTrip().getId(), ticket.getSeats().size());

        // 6. Logic Hoàn tiền (Quan trọng!)
        BigDecimal refundAmount = BigDecimal.ZERO;
        TicketCancelResponse.RefundDto refundDto = null;

        // CHỈ hoàn tiền nếu vé ĐÃ ĐƯỢC CONFIRM (Đã thanh toán)
        if (ticket.getStatus() == Ticket.TicketStatus.CONFIRMED) {
            // Có thể check thêm request.isRequestRefund() nếu muốn user xác nhận việc hoàn
            // tiền
            refundAmount = ticket.getTotalAmount().multiply(BigDecimal.valueOf(0.8));

            // TODO: Gọi Payment Service/Gateway để thực hiện refund thực tế tại đây
            // paymentService.refund(ticket.getPaymentId(), refundAmount);

            refundDto = TicketCancelResponse.RefundDto.builder()
                    .amount(refundAmount)
                    .percentage(80)
                    .processingTime("3-5 business days")
                    .refundMethod("original payment method")
                    .status("PROCESSING") // Trạng thái refund
                    .build();
        } else {
            // Vé chưa thanh toán (PENDING) -> Hủy bình thường, không hoàn tiền
            refundDto = TicketCancelResponse.RefundDto.builder()
                    .amount(BigDecimal.ZERO)
                    .status("NONE")
                    .build();
        }

        // 7. Cập nhật trạng thái vé và lưu
        ticket.setStatus(Ticket.TicketStatus.CANCELLED);
        ticket.setCancelledAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        return TicketCancelResponse.builder()
                .ticketId(ticket.getId())
                .status("cancelled")
                .cancelledAt(ticket.getCancelledAt())
                .refund(refundDto)
                .build();
    }

    public Page<TicketHistoryResponse> getUserTickets(String userEmail, String statusStr, LocalDate fromDate,
            LocalDate toDate, Pageable pageable) {
        Ticket.TicketStatus statusTemp = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                statusTemp = Ticket.TicketStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                /* ignore */ }
        }
        final Ticket.TicketStatus status = statusTemp;

        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Luôn filter theo userEmail
            predicates.add(cb.equal(root.get("userEmail"), userEmail));

            // Chỉ thêm điều kiện nếu tham số KHÁC NULL
            // -> Khắc phục hoàn toàn lỗi "could not determine data type" của Postgres
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromDateTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime));
            }
            if (toDateTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDateTime));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 4. Gọi Repository
        // Nhờ @EntityGraph ở Repository, lệnh này sẽ load luôn Trip/Route/Operator
        // trong 1 query
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
                        .route(ticket.getTrip().getRoute().getOrigin() + " → "
                                + ticket.getTrip().getRoute().getDestination())
                        .operator(ticket.getTrip().getOperator().getName())
                        .departureTime(ticket.getTrip().getDepartureTime())
                        .arrivalTime(ticket.getTrip().getArrivalTime())
                        .duration(ticket.getTrip().getRoute().getEstimatedMinutes())
                        .from(TicketDetailResponse.StopDto.builder()
                                .stopId(ticket.getPickupRouteStop().getId())
                                .name(ticket.getPickupRouteStop().getStation().getName())
                                .address(ticket.getPickupRouteStop().getFullAddress())
                                .time(ticket.getPickupTime())
                                .build())
                        .to(TicketDetailResponse.StopDto.builder()
                                .stopId(ticket.getDropoffRouteStop().getId())
                                .name(ticket.getDropoffRouteStop().getStation().getName())
                                .address(ticket.getDropoffRouteStop().getFullAddress())
                                .time(ticket.getDropoffTime())
                                .build())
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
                        .route(ticket.getTrip().getRoute().getOrigin() + " → "
                                + ticket.getTrip().getRoute().getDestination())
                        .departureTime(ticket.getTrip().getDepartureTime())
                        .operator(ticket.getTrip().getOperator().getName())
                        .build())
                .build();
    }

    /**
     * Finds a guest booking using a unique reference code and verification value
     * (phone or email).
     * 
     * @param request The guest lookup request DTO.
     * @return Aggregated TicketResponse for the booking.
     */
    @Transactional(readOnly = true)
    public TicketLookupResponse lookupGuestTicket(GuestLookupRequest request) {
        String reference = request.getTicketCode().toUpperCase().trim();
        String verification = request.getVerificationValue().trim();

        // 1. Find the representative ticket (fast fail if not found)
        Ticket representativeTicket = ticketRepository.findFirstByTicketCode(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for reference: " + reference));

        // 2. Perform verification (Security Check)
        boolean verified = verification.equalsIgnoreCase(representativeTicket.getContactEmail()) ||
                verification.equalsIgnoreCase(representativeTicket.getContactPhone());

        if (representativeTicket.getUserEmail() != null) {
            throw new ForbiddenException(
                    "This booking belongs to a registered user and must be retrieved via the user portal.");
        }

        if (!verified) {
            throw new ForbiddenException("Verification failed. Phone number or email does not match the booking.");
        }

        // 3. Aggregate all tickets sharing this reference
        // (Assuming future support for split tickets, or simply finding the single
        // ticket by unique code)
        List<Ticket> allTickets = ticketRepository.findAllByTicketCode(reference);

        if (allTickets.isEmpty()) {
            throw new ResourceNotFoundException("No tickets found for booking reference: " + reference);
        }

        // 4. Pass the LIST to the mapper (Fixes the error)
        return mapToTicketResponse(allTickets);
    }

    private TicketLookupResponse mapToTicketResponse(List<Ticket> tickets) {
        // Use the first ticket for shared data (Trip, Contact, Route)
        Ticket representative = tickets.get(0);
        Trip trip = representative.getTrip();
        String routeName = trip.getRoute().getOrigin() + " - " + trip.getRoute().getDestination();

        // Aggregate seats from all tickets
        List<String> allSeats = tickets.stream()
                .flatMap(t -> t.getSeats().stream())
                .distinct()
                .collect(Collectors.toList());

        // Aggregate total price
        BigDecimal totalAmount = tickets.stream()
                .map(Ticket::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TicketLookupResponse.builder()
                .ticketId(representative.getId())
                .ticketCode(representative.getTicketCode())
                .status(representative.getStatus().name().toLowerCase())
                .seats(allSeats) // All seats combined
                .contactName(representative.getContactName())
                .contactEmail(representative.getContactEmail())
                .contactPhone(representative.getContactPhone())
                .createdAt(representative.getCreatedAt())

                .pricing(TicketLookupResponse.PricingDto.builder()
                        .total(totalAmount)
                        .currency("VND")
                        .build())

                .tripDetails(TicketLookupResponse.TripDetailsDto.builder()
                        .tripId(trip.getId())
                        .route(routeName)
                        .operator(trip.getOperator().getName())
                        .departureTime(trip.getDepartureTime())
                        .arrivalTime(trip.getArrivalTime())
                        .build())
                .build();
    }
}