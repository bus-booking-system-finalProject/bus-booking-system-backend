package com.booking.bookingService.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // You can optionally define a custom Executor bean here 
    // to control thread pool size (e.g., max 10 concurrent email senders)
}