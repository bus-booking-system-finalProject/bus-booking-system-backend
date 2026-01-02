package com.booking.bookingService.config;

import com.booking.bookingService.annotation.CurrentOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OperatorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // This resolver triggers only when it sees @CurrentOperator on a UUID
        return parameter.getParameterAnnotation(CurrentOperator.class) != null 
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter, 
            ModelAndViewContainer mavContainer, 
            NativeWebRequest webRequest, 
            WebDataBinderFactory binderFactory
    ) {
        // --- REUSABLE LOGIC HERE ---
        
        // OPTION A: For Dev/Testing (Hardcoded "Phương Trang")
        // This fulfills your immediate request.
        /*
        return operatorRepository.findByName("Phương Trang").stream()
        .findFirst() // Converts Stream to Optional<Operator>
        .map(Operator::getId) // Now map is valid
        .orElseThrow(() -> new RuntimeException("Dev Error: Operator 'Phương Trang' not found in DB"));
        */
        
        // OPTION B: The "Real" Way (for later)
        // String userId = webRequest.getHeader("X-User-Id");
        // return UUID.fromString(userId);

        // 1. Get Operator ID from the Header injected by API Gateway / GatewayAuthenticationFilter
        String operatorIdHeader = webRequest.getHeader("X-Operator-Id");

        // 2. Validate and Return
        if (operatorIdHeader != null && !operatorIdHeader.isBlank()) {
            try {
                return UUID.fromString(operatorIdHeader);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid X-Operator-Id format in header");
            }
        }

        // 3. Handle Missing Header
        // If the endpoint requires @CurrentOperator but the user (e.g., ADMIN or Guest) 
        // doesn't have the ID, we throw an error or return null. 
        // Throwing error is safer to prevent data leaks.
        throw new RuntimeException("Missing X-Operator-Id header. User does not have OPERATOR/STAFF role.");
    }

}