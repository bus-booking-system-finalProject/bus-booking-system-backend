package com.booking.bookingService.config;

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

import java.io.IOException;
import java.util.List;

@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

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

            // 1. Lấy thông tin từ Header do Gateway truyền sang
            String userEmail = request.getHeader("X-User-Email");
            String userRole = request.getHeader("X-User-Role");

            // 2. Chỉ tạo Authentication nếu chưa có và Header hợp lệ
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Xử lý role: Nếu null hoặc rỗng -> gán ROLE_USER mặc định để không lỗi
                String roleName = (userRole != null && !userRole.isEmpty()) ? userRole : "USER";
                if (!roleName.startsWith("ROLE_")) {
                    roleName = "ROLE_" + roleName;
                }

                // 3. Tạo UserDetails (Giống logic trong UserController/UserService)
                // Chúng ta tạo một object User ảo (của Spring Security) vì service này không
                // truy cập bảng User
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleName));

                // Password để trống vì đã xác thực ở Gateway
                UserDetails userDetails = new User(userEmail, "", authorities);

                // 4. Tạo Authentication Token chứa UserDetails
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 5. Set vào Context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            System.err.println("GatewayAuthenticationFilter error: " + e.getMessage());
            e.printStackTrace();
            filterChain.doFilter(request, response);
        }
    }
}