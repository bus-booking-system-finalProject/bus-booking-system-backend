package com.booking.bookingService.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class TicketRequest {
    @NotNull(message = "Trip ID is required")
    private UUID tripId;

    @NotEmpty(message = "Seats must not be empty")
    private List<String> seats;

    @NotNull(message = "Pickup stop is required")
    private UUID pickupId;
    
    @NotNull(message = "Dropoff stop is required")
    private UUID dropoffId;

    @NotBlank(message = "Contact name is required")
    private String contactName;

    @Email(message = "Invalid email format")
    private String contactEmail;

    @NotBlank(message = "Contact phone is required")
    private String contactPhone;

    /**
     * Quan trọng: Session ID dùng để đối chiếu với Redis Lock.
     * - Nếu là Guest: Bắt buộc phải gửi lên (trùng với sessionId lúc gọi API lock).
     * - Nếu là User đã login: Có thể null (Backend sẽ dùng email từ Security Context), 
     * nhưng tốt nhất Frontend cứ gửi kèm sessionId thống nhất cho cả 2 luồng.
     */
    private String sessionId;
    
    @JsonProperty("isGuestCheckout")
    private boolean isGuestCheckout;
}