package com.booking.userService.client;

import com.booking.userService.config.FeignClientConfig;
import com.booking.userService.dto.OperatorDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import com.booking.userService.dto.ApiResponse;
import java.util.List;

// Assuming the booking service is named "booking-service" in Eureka/Consul
@FeignClient(name = "booking-service", configuration = FeignClientConfig.class) 
public interface BookingClient {
    
    @GetMapping("/operators") // Adjust path to match your booking-service API
    ApiResponse<List<OperatorDto>> getAllOperators();
}