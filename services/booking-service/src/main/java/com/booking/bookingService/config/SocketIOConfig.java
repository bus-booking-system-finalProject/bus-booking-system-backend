package com.booking.bookingService.config;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Configuration
public class SocketIOConfig {

    @Value("${socketio.port:9085}")
    private int socketIOPort;

    @Bean
    public SocketIOServer socketIOServer() {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(socketIOPort);
        config.setContext("/socket.io");
        // Allow all origins (use "*" for development, restrict in production)
        config.setOrigin("*");
        // Add ping timeout and interval for better connection stability
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        // Upgrade timeout
        config.setUpgradeTimeout(10000);
        return new SocketIOServer(config);
    }
}

@Component
@RequiredArgsConstructor
class SocketIORunner implements CommandLineRunner {
    private final SocketIOServer server;

    @Override
    public void run(String... args) throws Exception {
        try {
            int port = server.getConfiguration().getPort();
            System.out.println("Starting Socket.IO Server on port " + port + "...");
            server.start();
            System.out.println("--- Socket.IO Server đã chạy trên cổng " + port + " ---");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to start Socket.IO server");
            System.err.println("Exception type: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();

            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("address already in use") ||
                    errorMsg.contains("bind")) {
                System.err.println("\n[HINT] Port is already in use or cannot be bound");
                System.err.println("[SOLUTION] Change port in booking-service.yml: socketio.port=<new_port>");
            }
            if (errorMsg.contains("netty") ||
                    errorMsg.contains("channel")) {
                System.err.println("\n[HINT] Netty/Network error - possible port or binding issue");
                System.err.println("[SOLUTION] Check if port " + server.getConfiguration().getPort() + " is available");
            }

            System.err.println("\n[WARNING] Application will continue without Socket.IO");
        }
    }
}