package com.booking.bookingService.service;

import com.booking.bookingService.model.Booking;
import com.booking.bookingService.model.SeatStatus;
import com.booking.bookingService.repository.BookingRepository;
import com.booking.bookingService.repository.SeatStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupService {

    private final BookingRepository bookingRepository;
    private final SeatStatusRepository seatStatusRepository;
    private final RedisLockService redisLockService;

    // Chạy mỗi 1 phút (60000ms)
    @Scheduled(fixedRate = 60000) 
    @Transactional
    public void cleanupExpiredBookings() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. Tìm các đơn PENDING đã hết hạn
        List<Booking> expiredBookings = bookingRepository.findByStatusAndLockedUntilBefore(Booking.BookingStatus.PENDING, now);

        if (expiredBookings.isEmpty()) return;

        log.info("Found {} expired bookings. Cleaning up...", expiredBookings.size());

        for (Booking booking : expiredBookings) {
            try {
                // 2. Chuyển trạng thái Booking -> CANCELLED
                booking.setStatus(Booking.BookingStatus.CANCELLED);
                booking.setCancelledAt(now);
                
                // 3. Nhả ghế trong DB (SeatStatus -> AVAILABLE)
                // Lấy danh sách mã ghế từ booking
                List<String> seatCodes = booking.getPassengers().stream()
                        .map(p -> p.getSeatCode())
                        .collect(Collectors.toList());
                
                // Tìm các SeatStatus đang LOCKED thuộc trip này và mã ghế này
                List<SeatStatus> lockedSeats = seatStatusRepository.findByTripIdAndSeat_SeatCodeIn(booking.getTrip().getId(), seatCodes);
                
                for (SeatStatus seatStatus : lockedSeats) {
                    // Chỉ nhả nếu nó đang LOCKED (phòng trường hợp lỗi logic nào đó)
                    if (seatStatus.getState() == SeatStatus.SeatState.LOCKED) {
                        seatStatus.setState(SeatStatus.SeatState.AVAILABLE);
                        
                        // Xóa luôn key Redis cho chắc (dù có thể nó đã tự hết hạn)
                        String redisKey = "lock:seat:" + booking.getTrip().getId() + ":" + seatStatus.getSeat().getSeatCode();
                        redisLockService.unlock(redisKey);
                    }
                }
                
                // Lưu thay đổi ghế
                seatStatusRepository.saveAll(lockedSeats);
                
            } catch (Exception e) {
                log.error("Error cleaning up booking ID: " + booking.getId(), e);
            }
        }

        // 4. Lưu thay đổi Booking
        bookingRepository.saveAll(expiredBookings);
    }
}