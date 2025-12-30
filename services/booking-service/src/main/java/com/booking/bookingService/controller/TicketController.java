package com.booking.bookingService.controller;

import com.booking.bookingService.dto.ticket.CancelTicketRequest;
import com.booking.bookingService.dto.ticket.GuestLookupRequest;
import com.booking.bookingService.dto.ticket.SeatLockRequest;
import com.booking.bookingService.dto.ticket.TicketLookupResponse;
import com.booking.bookingService.dto.ticket.TicketRequest;
import com.booking.bookingService.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    // --- API 1: LOCK SEATS (Chọn ghế) ---
    @PostMapping("/lock")
    public ResponseEntity<?> lockSeats(
            @Valid @RequestBody SeatLockRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        
        // Gọi service để lock ghế. Nếu ghế đã bị lock bởi người khác, 
        // service sẽ throw Exception (Global Exception Handler sẽ bắt lỗi này)
        ticketService.lockSeats(request);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Seats locked successfully"
        ));
    }

    // --- API 2: UNLOCK SEATS (Bỏ chọn ghế) ---
    @PostMapping("/unlock")
    public ResponseEntity<?> unlockSeats(
            @RequestBody SeatLockRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        
        ticketService.unlockSeats(request);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Seats unlocked successfully"
        ));
    }

    // --- API 3: CREATE TICKET (Xác nhận đặt vé) ---
    @PostMapping
    public ResponseEntity<?> createTicket(
            @Valid @RequestBody TicketRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = null;
        
        // Logic xác định userEmail
        if (!request.isGuestCheckout()) {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            userEmail = currentUser.getUsername();
        } 
        
        System.out.println("Creating ticket for user: " + userEmail);
        // Service sẽ dùng userEmail (nếu có) HOẶC sessionId trong request để đối chiếu lock
        return new ResponseEntity<>(ticketService.createTicket(request, userEmail), HttpStatus.CREATED);
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> getTicketDetail(
            @PathVariable UUID ticketId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = (currentUser != null) ? currentUser.getUsername() : null;
        return ResponseEntity.ok(ticketService.getTicketDetail(ticketId, userEmail));
    }

    @PutMapping("/{ticketId}/cancel")
    public ResponseEntity<?> cancelTicket(
            @PathVariable UUID ticketId,
            @RequestBody CancelTicketRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = (currentUser != null) ? currentUser.getUsername() : null;
        return ResponseEntity.ok(ticketService.cancelTicket(ticketId, request, userEmail));
    }

    @GetMapping
    public ResponseEntity<?> getUserTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = ticketService.getUserTickets(currentUser.getUsername(), status, fromDate, toDate, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result.getContent());

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", result.getTotalElements());
        pagination.put("totalPages", result.getTotalPages());
        response.put("pagination", pagination);
        
        return ResponseEntity.ok(response);
    }

    // --- GUEST LOOKUP ---
    /**
     * Endpoint for guest users to lookup a ticket using the booking reference and a verification value (phone or email).
     * This endpoint does NOT require authentication.
     * @param request DTO containing bookingCode and verificationValue.
     * @return The aggregated ticket details.
     */
    @PostMapping("/lookup")
    public ResponseEntity<?> lookupGuestBooking(
            @RequestBody GuestLookupRequest request) {
        
        TicketLookupResponse data = ticketService.lookupGuestTicket(request);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);

        return ResponseEntity.ok(response);
    }
    
}