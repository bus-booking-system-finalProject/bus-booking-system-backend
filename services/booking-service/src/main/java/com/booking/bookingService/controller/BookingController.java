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

    /**
     * POST /bookings
     * Create a new booking (hold seats).
     */
    @PostMapping
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal UserDetails currentUser // Inject UserDetails
    ) {
        String userEmail = null;

        // Nếu không phải guest, yêu cầu phải có thông tin user đăng nhập
        if (!request.isGuestCheckout()) {
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User must be logged in for non-guest checkout");
            }
            userEmail = currentUser.getUsername(); // Lấy email từ UserDetails
        }

        // Gọi service
        var response = bookingService.createBooking(request, userEmail);
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * GET /bookings/{bookingId}
     * Get booking details.
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getBookingDetail(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = (currentUser != null) ? currentUser.getUsername() : null;
        
        var response = bookingService.getBookingDetail(bookingId, userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /bookings/{bookingId}/cancel
     * Cancel a booking.
     */
    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(
            @PathVariable UUID bookingId,
            @RequestBody CancelBookingRequest request,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        String userEmail = (currentUser != null) ? currentUser.getUsername() : null;
        
        var response = bookingService.cancelBooking(bookingId, request, userEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /bookings
     * Get user's booking history.
     */
    @GetMapping
    public ResponseEntity<?> getUserBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails currentUser // Inject UserDetails
    ) {
        // Bắt buộc phải login mới xem được lịch sử
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        String userEmail = currentUser.getUsername();

        // Tạo Pageable
        Pageable pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));

        var result = bookingService.getUserBookings(userEmail, status, fromDate, toDate, pageable);

        // Map response format chuẩn
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
}