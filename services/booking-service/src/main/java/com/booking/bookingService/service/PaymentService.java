package com.booking.bookingService.service;

import com.booking.bookingService.event.TicketSuccessEvent;
import com.booking.bookingService.exception.ResourceNotFoundException;
import com.booking.bookingService.model.PayOSPayment;
import com.booking.bookingService.model.Payment;
import com.booking.bookingService.model.Ticket;
import com.booking.bookingService.model.TripSeat;
import com.booking.bookingService.model.Payment.PaymentStatus;
import com.booking.bookingService.repository.PayOSPaymentRepository;
import com.booking.bookingService.repository.PaymentRepository;
import com.booking.bookingService.repository.TicketRepository;
import com.booking.bookingService.repository.TripSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PayOS payOS;
    private final PaymentRepository paymentRepository;
    private final TicketRepository ticketRepository;
    private final TripSeatRepository tripSeatRepository; 
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    private PayOSPaymentRepository payOSPaymentRepository;

    @Transactional
    public CreatePaymentLinkResponse createPaymentLink(UUID ticketId, String returnUrl, String cancelUrl) throws Exception {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        if (ticket.getStatus() == Ticket.TicketStatus.CONFIRMED) {
            throw new IllegalStateException("Ticket is already paid");
        }

        // 1. Tạo OrderCode
        long orderCode = System.currentTimeMillis(); 

        // 2. Description
        String description = "Ticket " + ticket.getTicketCode();
        description = description.replaceAll("[^a-zA-Z0-9 ]", "");
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        // 3. Save Payment
        PayOSPayment payment = PayOSPayment.builder()
                .ticket(ticket)
                .amount(ticket.getTotalAmount())
                .orderCode(orderCode)
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        paymentRepository.save(payment);

        // 4. Create Item
        PaymentLinkItem item = PaymentLinkItem.builder()
                .name("Bus Ticket")
                .quantity(1)
                .price(ticket.getTotalAmount().longValue())
                .build();

        // 5. Create Request
        CreatePaymentLinkRequest paymentRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(ticket.getTotalAmount().longValue())
                .description(description)
                .cancelUrl(cancelUrl)
                .returnUrl(returnUrl)
                .item(item)
                .build();

        return payOS.paymentRequests().create(paymentRequest);
    }

    @Transactional
    public void processWebhook(WebhookData webhookData) {
        // Tìm Payment theo OrderCode
        Long orderCode = webhookData.getOrderCode();
        PayOSPayment payment = payOSPaymentRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for OrderCode: " + orderCode));

        if (PaymentStatus.PAID.equals(payment.getStatus())) return;

        // Cập nhật trạng thái Payment
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Cập nhật vé
        Ticket ticket = payment.getTicket();
        if (ticket.getStatus() == Ticket.TicketStatus.CANCELLED) {
            log.warn("Ticket {} cancelled but payment received. Need refund processing.", ticket.getTicketCode());
            payment.setStatus(Payment.PaymentStatus.REFUNDING);

            // TODO: Need refund logic

            paymentRepository.save(payment);
            return;
        }

        ticket.setStatus(Ticket.TicketStatus.CONFIRMED);
        ticket.setConfirmedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        // Cập nhật trạng thái ghế -> BOOKED
        // Tìm các ghế thuộc chuyến xe này và có mã ghế trùng với vé
        List<TripSeat> seats = tripSeatRepository.findByTripIdAndSeat_SeatCodeIn(
                ticket.getTrip().getId(), 
                ticket.getSeats()
        );
        
        for (TripSeat seat : seats) {
            seat.setStatus(TripSeat.Status.BOOKED);
        }
        tripSeatRepository.saveAll(seats);

        // Gửi email vé
        eventPublisher.publishEvent(new TicketSuccessEvent(ticket));
        log.info("Payment success processing complete for Ticket: {}", ticket.getTicketCode());
    }

    public Payment getPaymentByTicketId(UUID ticketId) {
        return paymentRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment info not found for ticket: " + ticketId));
    }
}