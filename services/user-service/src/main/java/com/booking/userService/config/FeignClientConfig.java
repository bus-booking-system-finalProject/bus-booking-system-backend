package com.booking.userService.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Simulate a System Admin user for internal calls
            requestTemplate.header("X-User-Email", "system-internal@vexesieure.com");
            requestTemplate.header("X-User-Role", "ADMIN");
        };
    }
}