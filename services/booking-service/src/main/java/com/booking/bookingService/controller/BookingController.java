package com.booking.bookingService.controller;

import com.booking.bookingService.dto.BookingRequest;
import com.booking.bookingService.dto.CancelBookingRequest;
import com.booking.bookingService.service.BookingService;
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
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = null;

        if (!request.isGuestCheckout()) {
            if (currentUser == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("data", null);
                errorResponse.put("message", "User must be logged in for non-guest checkout");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            userEmail = currentUser.getUsername();
        }

        var result = bookingService.createBooking(request, userEmail);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        response.put("message", "Booking created successfully");
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getBookingDetail(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = (currentUser != null) ? currentUser.getUsername() : null;
        
        var result = bookingService.getBookingDetail(bookingId, userEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        response.put("message", "Booking details retrieved successfully");

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable UUID bookingId,
            @RequestBody CancelBookingRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = (currentUser != null) ? currentUser.getUsername() : null;
        
        var result = bookingService.cancelBooking(bookingId, request, userEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result);
        response.put("message", "Booking cancelled successfully");

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<?> getUserBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        if (currentUser == null) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("data", null);
            errorResponse.put("message", "Unauthorized access to booking history");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        }
        
        String userEmail = currentUser.getUsername();

        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = bookingService.getUserBookings(userEmail, status, fromDate, toDate, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", result.getContent());
        response.put("message", "Booking history retrieved successfully");
        
        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", result.getTotalElements());
        pagination.put("totalPages", result.getTotalPages());
        response.put("pagination", pagination);

        return ResponseEntity.ok(response);
    }
}