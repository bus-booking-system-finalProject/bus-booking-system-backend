package com.booking.bookingService.service;

import com.booking.bookingService.model.Ticket;
import com.booking.bookingService.repository.TicketRepository;
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
public class TicketCompletionService {

    private final TicketRepository ticketRepository;

    // Chạy mỗi 1 giờ (3600000 ms) hoặc tùy chỉnh
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void markTicketsAsCompleted() {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. Tìm các vé CONFIRMED mà chuyến xe đã về bến
        List<Ticket> completedTickets = ticketRepository.findCompletedTickets(now);

        if (completedTickets.isEmpty()) return;

        log.info("Found {} tickets to mark as COMPLETED.", completedTickets.size());

        // 2. Cập nhật trạng thái
        for (Ticket ticket : completedTickets) {
            ticket.setStatus(Ticket.TicketStatus.COMPLETED);
        }

        // 3. Lưu vào DB
        ticketRepository.saveAll(completedTickets);
    }
}