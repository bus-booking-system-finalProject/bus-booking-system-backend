package com.booking.userService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.booking.userService.service.UserDetailsServiceImpl;

@Configuration
public class SecurityConfig {

    private final GatewayHeaderAuthenticationFilter gatewayHeaderFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    public SecurityConfig(
            GatewayHeaderAuthenticationFilter gatewayHeaderFilter,
            UserDetailsServiceImpl userDetailsService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler
    ) {
        this.gatewayHeaderFilter = gatewayHeaderFilter;
        this.userDetailsService = userDetailsService;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for stateless API
            .logout(AbstractHttpConfigurer::disable) // Disable default logout (Let UserController handle it)
            .authorizeHttpRequests(authz -> authz
                // Make our register and login endpoints public
                // Allow OAuth2 endpoints
                .requestMatchers(
                    "/register", "/login", "/refresh", "/logout",
                    "/login/oauth2/**", "/oauth2/**",
                    "/verify-email", "/forgot-password", "/reset-password",
                    "/v3/api-docs/**", "/v3/api-docs", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Only users with the "ADMIN" authority can access /admin/**
                .requestMatchers("/admin/**").hasAuthority("ADMIN")
                // Both ADMIN and USER can access /api/**
                .requestMatchers("/api/**").hasAnyAuthority("USER", "ADMIN")
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            // Tell Spring Security to be stateless (no sessions)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Tell Spring Security which auth provider to use
            .authenticationProvider(authenticationProvider())
            // Add our JWT filter *before* the default username/password filter
            .addFilterBefore(gatewayHeaderFilter, UsernamePasswordAuthenticationFilter.class)
            // --- ADD OAUTH2 LOGIN CONFIGURATION ---
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2LoginSuccessHandler)
            );
        return http.build();
    }

    // This bean provides the AuthenticationManager to our Controller
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // This bean tells Spring how to check passwords
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService); 
        authProvider.setPasswordEncoder(passwordEncoder()); // Tell it how to hash passwords
        return authProvider;
    }
}