package com.booking.bookingService.service;


import com.booking.bookingService.dto.*;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.*;
import com.booking.bookingService.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
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
public class BookingService {

    private final RedisLockService redisLockService;
    private final SeatStatusRepository seatStatusRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;

    private static final long LOCK_TIMEOUT_SECONDS = 600; // 10 minutes

    // --- 1. CREATE BOOKING ---
    @Transactional
    public BookingResponse createBooking(BookingRequest request, String userEmail) {
        // A. Validate Trip
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));

        // B. Hold Seats (Logic Redis Lock + DB Update)
        // UserID truyền vào Redis sẽ là userEmail hoặc "GUEST" nếu không có login
        String userIdForLock = userEmail != null ? userEmail : "GUEST-" + UUID.randomUUID();
        holdSeatsInternal(trip.getId(), request.getSeats(), userIdForLock);

        // C. Calculate Pricing
        BigDecimal pricePerTicket = trip.getPrice();
        BigDecimal subtotal = pricePerTicket.multiply(BigDecimal.valueOf(request.getSeats().size()));
        BigDecimal serviceFee = BigDecimal.valueOf(20000); // Phí cố định ví dụ
        BigDecimal total = subtotal.add(serviceFee);

        // D. Create Booking Entity
        Booking booking = Booking.builder()
                .bookingReference("BK" + System.currentTimeMillis()) // Simple ID generation
                .userId(userEmail)
                .trip(trip)
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .totalAmount(total)
                .status(Booking.BookingStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .lockedUntil(LocalDateTime.now().plusSeconds(LOCK_TIMEOUT_SECONDS))
                .build();

        // E. Create Passengers
        List<Passenger> passengers = request.getPassengers().stream().map(p -> 
            Passenger.builder()
                .booking(booking)
                .fullName(p.getFullName())
                .documentId(p.getDocumentId())
                .phone(p.getPhone())
                .seatCode(p.getSeatCode())
                .build()
        ).collect(Collectors.toList());
        booking.setPassengers(passengers);

        bookingRepository.save(booking);

        // F. Return Response
        return BookingResponse.builder()
                .bookingId(booking.getId())
                .tripId(trip.getId())
                .status("pending")
                .seats(request.getSeats())
                .passengers(passengers.size())
                .pricing(BookingResponse.PricingDto.builder()
                        .subtotal(subtotal)
                        .serviceFee(serviceFee)
                        .total(total)
                        .currency("VND")
                        .build())
                .lockedUntil(booking.getLockedUntil())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    // --- 2. GET DETAIL ---
    @Transactional // Thêm Transactional vì có thể update DB
    public BookingDetailResponse getBookingDetail(UUID bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        // Security check
        if (userEmail != null && !userEmail.equals(booking.getUserId())) {
             // throw new AccessDeniedException...
        }

        // [NEW] LAZY CHECK: Nếu đang PENDING mà quá giờ -> Cancel luôn ngay lúc này
        if (booking.getStatus() == Booking.BookingStatus.PENDING && 
            booking.getLockedUntil().isBefore(LocalDateTime.now())) {
            
            // Gọi hàm xử lý cancel (Reuse logic)
            // Lưu ý: Cần refactor logic releaseSeats ra để gọi ở đây
            expireBookingNow(booking); 
        }

        return mapToDetailResponse(booking);
    }

    // Helper method mới để xử lý hết hạn tức thì
    private void expireBookingNow(Booking booking) {
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        
        List<String> seatCodes = booking.getPassengers().stream()
                .map(p -> p.getSeatCode()).collect(Collectors.toList());
        
        releaseSeats(booking.getTrip().getId(), seatCodes); // Hàm này bạn đã có sẵn ở dưới
        bookingRepository.save(booking);
    }

    // --- 3. CANCEL BOOKING ---
    @Transactional
    public BookingCancelResponse cancelBooking(UUID bookingId, CancelBookingRequest request, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new IllegalStateException("Booking is already cancelled");
        }

        // Logic check thời gian hủy (VD: không hủy trước giờ đi 2 tiếng)
        if (booking.getTrip().getDepartureTime().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Cannot cancel booking close to departure time");
        }

        // 1. Release Seats
        List<String> seatCodes = booking.getPassengers().stream()
                .map(Passenger::getSeatCode).collect(Collectors.toList());
        releaseSeats(booking.getTrip().getId(), seatCodes);

        // 2. Update Booking Status
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // 3. Calculate Refund (Demo: 80%)
        BigDecimal refundAmount = BigDecimal.ZERO;
        if (request.isRequestRefund()) {
            refundAmount = booking.getTotalAmount().multiply(BigDecimal.valueOf(0.8));
        }

        return BookingCancelResponse.builder()
                .bookingId(booking.getId())
                .status("cancelled")
                .cancelledAt(booking.getCancelledAt())
                .refund(BookingCancelResponse.RefundDto.builder()
                        .amount(refundAmount)
                        .percentage(80)
                        .processingTime("3-5 business days")
                        .refundMethod("original payment method")
                        .build())
                .build();
    }

    // --- 4. GET HISTORY (FIXED) ---
    public Page<BookingHistoryResponse> getUserBookings(String userEmail, String statusStr, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        
        // 1. Parse Status (Giữ nguyên logic cũ)
        Booking.BookingStatus statusTemp = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                statusTemp = Booking.BookingStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) { /* ignore */ }
        }
        final Booking.BookingStatus status = statusTemp; // Biến final để dùng trong lambda

        // 2. Parse Dates (Giữ nguyên logic cũ)
        LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDateTime = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        // 3. Tạo Specification (Query động)
        // Logic: Chỉ khi nào tham số khác null thì mới add điều kiện vào câu SQL
        Specification<Booking> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Bắt buộc: Lọc theo User ID
            predicates.add(cb.equal(root.get("userId"), userEmail));

            // Tùy chọn: Lọc theo Status nếu có
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Tùy chọn: Lọc từ ngày
            if (fromDateTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDateTime));
            }

            // Tùy chọn: Lọc đến ngày
            if (toDateTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDateTime));
            }

            // Kết hợp tất cả điều kiện bằng toán tử AND
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 4. Gọi Repository
        // Nhờ extends JpaSpecificationExecutor nên có hàm findAll(Specification, Pageable)
        Page<Booking> bookings = bookingRepository.findAll(spec, pageable);

        return bookings.map(this::mapToHistoryResponse);
    }

    // --- HELPERS ---

    private void holdSeatsInternal(UUID tripId, List<String> seatCodes, String userId) {
        List<String> lockedKeys = new ArrayList<>();
        try {
            // Redis Lock
            for (String seatCode : seatCodes) {
                String key = "lock:seat:" + tripId + ":" + seatCode;
                boolean acquired = redisLockService.tryLock(key, userId, LOCK_TIMEOUT_SECONDS);
                if (!acquired) {
                    throw new IllegalStateException("Seat " + seatCode + " is currently selected by another user.");
                }
                lockedKeys.add(key);
            }

            // DB Update
            // Cần thêm method này vào SeatStatusRepository
            // List<SeatStatus> statuses = seatStatusRepository.findByTripIdAndSeat_SeatCodeIn(tripId, seatCodes);
            // Ở đây mình dùng logic tìm thủ công nếu chưa có method trong repo
            List<SeatStatus> allStatuses = seatStatusRepository.findByTripId(tripId);
            List<SeatStatus> targetStatuses = allStatuses.stream()
                    .filter(s -> seatCodes.contains(s.getSeat().getSeatCode()))
                    .collect(Collectors.toList());

            if (targetStatuses.size() != seatCodes.size()) {
                throw new ResourceNotFoundException("Some seats are invalid");
            }

            for (SeatStatus status : targetStatuses) {
                if (status.getState() != SeatStatus.SeatState.AVAILABLE) {
                    throw new IllegalStateException("Seat " + status.getSeat().getSeatCode() + " is not available.");
                }
                status.setState(SeatStatus.SeatState.LOCKED);
            }
            seatStatusRepository.saveAll(targetStatuses);

        } catch (Exception e) {
            // Rollback Redis
            for (String key : lockedKeys) {
                redisLockService.unlock(key);
            }
            throw e;
        }
    }

    private void releaseSeats(UUID tripId, List<String> seatCodes) {
        // 1. Clear Redis Locks
        for (String seatCode : seatCodes) {
            String key = "lock:seat:" + tripId + ":" + seatCode;
            redisLockService.unlock(key);
        }

        // 2. Update DB to AVAILABLE
        List<SeatStatus> allStatuses = seatStatusRepository.findByTripId(tripId);
        List<SeatStatus> targetStatuses = allStatuses.stream()
                .filter(s -> seatCodes.contains(s.getSeat().getSeatCode()))
                .collect(Collectors.toList());
        
        for (SeatStatus status : targetStatuses) {
            status.setState(SeatStatus.SeatState.AVAILABLE);
        }
        seatStatusRepository.saveAll(targetStatuses);
    }

    private BookingDetailResponse mapToDetailResponse(Booking booking) {
        List<BookingDetailResponse.PassengerDto> passengerDtos = booking.getPassengers().stream()
                .map(p -> BookingDetailResponse.PassengerDto.builder()
                        .fullName(p.getFullName())
                        .documentId(p.getDocumentId())
                        .seatCode(p.getSeatCode())
                        .build())
                .collect(Collectors.toList());

        return BookingDetailResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())
                .status(booking.getStatus().name().toLowerCase())
                .createdAt(booking.getCreatedAt())
                .confirmedAt(booking.getConfirmedAt())
                .tripDetails(BookingDetailResponse.TripDetailsDto.builder()
                        .tripId(booking.getTrip().getId())
                        .route(booking.getTrip().getRoute().getOrigin() + " → " + booking.getTrip().getRoute().getDestination())
                        .operator(booking.getTrip().getOperator().getName())
                        .departureTime(booking.getTrip().getDepartureTime())
                        .arrivalTime(booking.getTrip().getArrivalTime())
                        .build())
                .passengers(passengerDtos)
                .pricing(BookingResponse.PricingDto.builder()
                        .total(booking.getTotalAmount())
                        .currency("VND")
                        .build())
                .build();
    }

    private BookingHistoryResponse mapToHistoryResponse(Booking booking) {
        List<String> seats = booking.getPassengers().stream()
                .map(Passenger::getSeatCode)
                .collect(Collectors.toList());

        return BookingHistoryResponse.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus().name().toLowerCase())
                .createdAt(booking.getCreatedAt())
                .seats(seats)
                .trip(BookingHistoryResponse.TripSummaryDto.builder()
                        .route(booking.getTrip().getRoute().getOrigin() + " → " + booking.getTrip().getRoute().getDestination())
                        .departureTime(booking.getTrip().getDepartureTime())
                        .operator(booking.getTrip().getOperator().getName())
                        .build())
                .build();
    }
}