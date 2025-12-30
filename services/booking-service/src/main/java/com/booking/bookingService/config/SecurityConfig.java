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

import com.booking.bookingService.security.CustomAccessDeniedHandler;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception -> exception
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                // 1. Cấu hình cơ bản
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error").permitAll()
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()

                        // 2. LOGIC VÉ (TICKET) - QUAN TRỌNG:
                        // a. Guest Lock & Unlock ghế (Mới thêm) -> Phải khai báo rõ ràng
                        .requestMatchers(HttpMethod.POST, "/tickets/lock", "/tickets/unlock").permitAll()
                        .requestMatchers(HttpMethod.POST, "/tickets/lookup").permitAll()
                        // b. Guest tạo vé (Submit form) -> POST /tickets
                        .requestMatchers(HttpMethod.POST, "/tickets").permitAll()

                        // c. Guest hủy vé (PUT /tickets/{uuid}/cancel)
                        .requestMatchers(HttpMethod.PUT, "/tickets/**").permitAll()

                        .requestMatchers("/payments/**").permitAll()

                        // d. Guest xem chi tiết vé (GET /tickets/{uuid})
                        // Chỉ khớp UUID để tránh trùng với trang lịch sử (GET /tickets)
                        .requestMatchers(HttpMethod.GET, "/tickets/{id:[0-9a-fA-F-]{36}}").permitAll()

                // 3. MASTER DATA
                .requestMatchers("/trips/**").permitAll()

                // ADMIN
                .requestMatchers("/operators/**", "/buses**", "/routes/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.GET, "/feedback/operators/**").permitAll()
                
                .requestMatchers(HttpMethod.POST, "/feedback").authenticated()

                        // 4. API CÒN LẠI -> Cần đăng nhập (Bao gồm xem lịch sử GET /tickets)
                        .anyRequest().authenticated());

        return http.build();
    }
}