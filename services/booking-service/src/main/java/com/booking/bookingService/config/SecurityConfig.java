package com.booking.bookingService.config;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            
            // 1. QUAN TRỌNG: Không lưu Session (Stateless) vì đã dùng JWT từ Gateway
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            .authorizeHttpRequests(auth -> auth
                // 2. Cho phép CORS Preflight (tránh lỗi khi gọi từ React/Frontend)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 3. FIX LỖI 401 ẢO: Cho phép Spring forward lỗi (Validation, 404, 500) ra ngoài
                .requestMatchers("/error").permitAll()
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                // 4. LOGIC VÉ (TICKET):
                // a. Guest tạo vé (POST /tickets) -> Public
                .requestMatchers(HttpMethod.POST, "/tickets").permitAll()

                // b. Guest hủy vé (PUT /tickets/{uuid}/cancel) -> Public
                .requestMatchers(HttpMethod.PUT, "/tickets/**").permitAll()

                // c. Guest xem chi tiết vé (GET /tickets/{uuid}) -> Public
                // Sử dụng Regex để chỉ khớp nếu đó là UUID, tránh nhầm với GET /tickets (lịch sử)
                .requestMatchers(HttpMethod.GET, "/tickets/{id:[0-9a-fA-F-]{36}}").permitAll()

                // 5. MASTER DATA (Chuyến xe, Nhà xe, Tuyến đường...) -> Public
                .requestMatchers("/trips/**", "/buses/**", "/routes/**", "/operators/**").permitAll()

                // 6. CÁC API CÒN LẠI -> BẮT BUỘC ĐĂNG NHẬP
                // (Bao gồm API: GET /tickets để xem lịch sử đặt vé)
                .anyRequest().authenticated()
            );

        return http.build();
    }
}