package com.booking.bookingService.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Socket.IO is handled by a separate server on port 9085
    // No need for Spring to serve socket.io resources
    // This class can be extended later for additional web configurations
}
