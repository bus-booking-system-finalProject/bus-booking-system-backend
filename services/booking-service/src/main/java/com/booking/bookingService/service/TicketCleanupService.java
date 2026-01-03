package com.booking.bookingService.service;

import com.booking.bookingService.model.Ticket;
import com.booking.bookingService.model.Trip;
import com.booking.bookingService.model.TripSeat;
import com.booking.bookingService.repository.TicketRepository;
import com.booking.bookingService.repository.TripSeatRepository;
import com.booking.bookingService.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketCleanupService {

    private final TicketRepository ticketRepository;
    private final TripSeatRepository seatStatusRepository;
    private final TripRepository tripRepository;
    private final RedisLockService redisLockService;

    // Chạy mỗi 1 phút (60000ms) để quét vé hết hạn
    @Scheduled(fixedRate = 60000) 
    @Transactional
    public void cleanupExpiredTickets() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. Tìm các vé (Ticket) trạng thái PENDING đã quá hạn lockedUntil
        List<Ticket> expiredTickets = ticketRepository.findByStatusAndLockedUntilBefore(Ticket.TicketStatus.PENDING, now);

        if (expiredTickets.isEmpty()) return;

        log.info("Found {} expired tickets. Cleaning up...", expiredTickets.size());

        for (Ticket ticket : expiredTickets) {
            try {
                // 2. Chuyển trạng thái Ticket -> CANCELLED
                ticket.setStatus(Ticket.TicketStatus.CANCELLED);
                ticket.setCancelledAt(now);
                
                // Lấy danh sách ghế trực tiếp từ Ticket (ElementCollection)
                List<String> seatCodes = ticket.getSeats();
                
                // Tìm các SeatStatus đang LOCKED thuộc trip này và mã ghế này
                // (Đảm bảo bạn đã có method này trong SeatStatusRepository)
                List<TripSeat> lockedSeats = seatStatusRepository.findByTripIdAndSeat_SeatCodeIn(ticket.getTrip().getId(), seatCodes);
                
                if (lockedSeats != null && !lockedSeats.isEmpty()) {
                    for (TripSeat seatStatus : lockedSeats) {
                        // Chỉ nhả nếu nó đang LOCKED (phòng trường hợp lỗi logic)
                        if (seatStatus.getStatus() == TripSeat.Status.LOCKED) {
                            seatStatus.setStatus(TripSeat.Status.AVAILABLE);
                            
                            // Xóa key Redis cho chắc chắn
                            String redisKey = "lock:seat:" + ticket.getTrip().getId() + ":" + seatStatus.getSeat().getSeatCode();
                            redisLockService.unlock(redisKey);
                        }
                    }
                    // Lưu thay đổi trạng thái ghế
                    seatStatusRepository.saveAll(lockedSeats);
                }

                Trip trip = ticket.getTrip();
                // Đảm bảo không cộng vượt quá sức chứa (phòng hờ)
                int newAvailable = trip.getAvailableSeats() + seatCodes.size();
                if (newAvailable <= trip.getBusModel().getSeatCapacity()) {
                    trip.setAvailableSeats(newAvailable);
                    tripRepository.save(trip);
                }
                
            } catch (Exception e) {
                log.error("Error cleaning up ticket ID: " + ticket.getId(), e);
            }
        }

        // 4. Lưu thay đổi Ticket (đã chuyển sang CANCELLED)
        ticketRepository.saveAll(expiredTickets);
    }
}