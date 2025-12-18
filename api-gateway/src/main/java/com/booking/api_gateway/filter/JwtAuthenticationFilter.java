package com.booking.api_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${app.security.public-endpoints}")
    private List<String> publicEndpoints;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }
        
        String path = exchange.getRequest().getURI().getPath();

        // 1. Kiểm tra xem request này có phải là Public không
        boolean isPublic = publicEndpoints.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        // 2. Kiểm tra xem Request có mang theo Token không
        List<String> authHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        String token = null;
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String headerValue = authHeaders.get(0);
            if (headerValue != null && headerValue.startsWith("Bearer ")) {
                token = headerValue.substring(7);
            }
        }

        // TRƯỜNG HỢP 1: Không có Token
        if (token == null) {
            if (isPublic) {
                // Nếu là Public -> Cho qua (Guest mode)
                return chain.filter(exchange);
            } else {
                // Nếu là Private -> Bắt buộc Login -> Lỗi 401
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        }

        // TRƯỜNG HỢP 2: Có Token -> Thử Validate
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSignInKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Nếu Token ngon -> Gắn thông tin vào Header
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-User-Email", claims.getSubject())
                    .header("X-User-Role", claims.get("role", String.class))
                    .build();

            return chain.filter(exchange.mutate().request(request).build());

        } catch (Exception e) {
            // Token bị lỗi (Hết hạn hoặc Fake)
            
            if (isPublic) {
                // QUAN TRỌNG: Nếu là endpoint public, dù token lỗi vẫn cho qua 
                // (coi như user đó là Guest)
                return chain.filter(exchange);
            }
            
            // Nếu là private endpoint mà token lỗi -> Chặn 401
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}