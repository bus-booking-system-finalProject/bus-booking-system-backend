package com.booking.bookingService.service;

import com.corundumstudio.socketio.SocketIOServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SocketIOService {

    private final SocketIOServer server;

    @PostConstruct
    public void startServer() {
        // Listen for clients joining a trip's real-time room
        server.addEventListener("join_trip", String.class, (client, tripId, ackRequest) -> {
            client.joinRoom(tripId);
            log.info("Client {} joined room for trip: {}", client.getSessionId(), tripId);
        });

        // Listen for clients leaving a trip's room
        server.addEventListener("leave_trip", String.class, (client, tripId, ackRequest) -> {
            client.leaveRoom(tripId);
            log.info("Client {} left room for trip: {}", client.getSessionId(), tripId);
        });

        // Listen for clients subscribing to booking updates
        server.addEventListener("subscribe_booking", String.class, (client, ticketCode, ackRequest) -> {
            client.joinRoom("booking:" + ticketCode);
            log.info("Client {} subscribed to booking: {}", client.getSessionId(), ticketCode);
        });

        // Listen for clients unsubscribing from booking updates
        server.addEventListener("unsubscribe_booking", String.class, (client, ticketCode, ackRequest) -> {
            client.leaveRoom("booking:" + ticketCode);
            log.info("Client {} unsubscribed from booking: {}", client.getSessionId(), ticketCode);
        });

        // Log when clients disconnect
        server.addDisconnectListener(client -> {
            log.info("Client {} disconnected", client.getSessionId());
        });
    }

    @PreDestroy
    public void stopServer() {
        server.stop();
    }

    /**
     * Broadcasts seat status changes to everyone viewing the trip.
     * 
     * @param tripId The trip being updated.
     * @param seats  List of seat codes (e.g., ["A1", "A2"]).
     * @param status The new status: 'locked', 'available', or 'booked'.
     */
    public void broadcastSeatUpdate(UUID tripId, List<String> seats, String status) {
        String roomId = tripId.toString();
        log.info("Broadcasting seat update to room {}: {} seats with status '{}'", roomId, seats.size(), status);
        server.getRoomOperations(roomId).sendEvent("seat_update",
                Map.of("tripId", tripId.toString(), "seats", seats, "status", status));
        log.debug("Seat update broadcast completed for trip: {}", tripId);
    }

    /**
     * Sends a booking confirmation to a specific ticket's subscribers.
     * Also broadcasts globally for logged-in users.
     */
    public void sendBookingConfirmation(String ticketCode, Object ticketData) {
        String roomId = "booking:" + ticketCode;
        log.info("Sending booking confirmation to room: {}", roomId);
        server.getRoomOperations(roomId).sendEvent("booking_confirmed", ticketData);
        // Also broadcast globally for any listeners
        server.getBroadcastOperations().sendEvent("booking_confirmed", ticketData);
    }

    /**
     * Broadcasts trip status changes (DELAYED, CANCELLED, COMPLETED) to everyone viewing the trip.
     * 
     * @param tripId       The trip being updated.
     * @param status       The new status: 'SCHEDULED', 'DELAYED', 'CANCELLED', or 'COMPLETED'.
     * @param delayMinutes Minutes of delay (optional, only for DELAYED status).
     * @param message      Optional message with more details.
     */
    public void broadcastTripStatus(UUID tripId, String status, Integer delayMinutes, String message) {
        String roomId = tripId.toString();
        log.info("Broadcasting trip status update to room {}: status '{}'", roomId, status);
        
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("tripId", tripId.toString());
        payload.put("status", status);
        payload.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (delayMinutes != null) {
            payload.put("delayMinutes", delayMinutes);
        }
        if (message != null && !message.isEmpty()) {
            payload.put("message", message);
        }
        
        server.getRoomOperations(roomId).sendEvent("trip_status", payload);
        log.debug("Trip status broadcast completed for trip: {}", tripId);
    }

    /**
     * Simplified trip status broadcast without delay minutes.
     */
    public void broadcastTripStatus(UUID tripId, String status, String message) {
        broadcastTripStatus(tripId, status, null, message);
    }

    /**
     * Sends a general notification to all connected clients.
     */
    public void broadcastNotification(String type, String title, String message) {
        log.info("Broadcasting notification: {} - {}", type, title);
        server.getBroadcastOperations().sendEvent("notification", Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "title", title,
                "message", message,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ));
    }
}