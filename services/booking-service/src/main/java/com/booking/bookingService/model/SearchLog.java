// src/main/java/com/booking/bookingService/model/SearchLog.java
package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SearchLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String origin;
    private String destination;
    
    private LocalDate travelDate; // The date user wanted to go
    private LocalDateTime searchedAt; // The time they clicked "Search"
}