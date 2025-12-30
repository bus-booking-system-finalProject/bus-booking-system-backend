package com.booking.userService.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.booking.userService.service.JwtService;
import com.booking.userService.service.UserDetailsServiceImpl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Component
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public GatewayHeaderAuthenticationFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getServletPath();
            // Bỏ qua kiểm tra cho các đường dẫn này
            if (path.contains("/v3/api-docs") || path.contains("/swagger-ui")) {
                filterChain.doFilter(request, response);
                return;
            }
            // Prefer gateway-provided headers; fallback to JWT for direct calls (local/dev)
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                String userEmail = request.getHeader("X-User-Email");
                String userRole = request.getHeader("X-User-Role");

                if (userEmail != null) {
                    List<SimpleGrantedAuthority> authorities = (userRole != null)
                            ? List.of(new SimpleGrantedAuthority(userRole))
                            : Collections.emptyList();

                    setAuthentication(request, new User(userEmail, "", authorities));
                } else {
                    // Direct call: parse Authorization header if present
                    String authHeader = request.getHeader(AUTHORIZATION);
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        try {
                            String username = jwtService.extractUsername(token);
                            if (username != null && !jwtService.isTokenExpired(token)) {
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                if (jwtService.isTokenValid(token, userDetails)) {
                                    setAuthentication(request, userDetails);
                                }
                            }
                        } catch (Exception ignored) {
                            // Invalid token: leave context empty and let entry point handle 401
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            System.err.println("GatewayAuthenticationFilter error: " + e.getMessage());
            e.printStackTrace();
            filterChain.doFilter(request, response);
        }
    }

    private void setAuthentication(HttpServletRequest request, UserDetails userDetails) {
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}