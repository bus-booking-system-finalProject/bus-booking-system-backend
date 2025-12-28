package com.booking.bookingService.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Bus Booking API")
                                                .version("1.0")
                                                .description("Tài liệu API cho Bus Booking System"))
                                // Prefer going through the API Gateway
                                .addServersItem(new Server().url("http://localhost:8080/booking/")
                                                .description("Gateway /booking/"))
                                // Optional direct service URL for local testing
                                .addServersItem(new Server().url("http://localhost:8083/")
                                                .description("Direct service"))
                                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                                .components(new Components()
                                                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                                                .name("bearerAuth")
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("bearer")
                                                                .bearerFormat("JWT")));
        }

        @Bean
        public GroupedOpenApi publicApi() {
                return GroupedOpenApi.builder()
                                .group("booking-service")
                                .pathsToMatch("/**")
                                .build();
        }
}