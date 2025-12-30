package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "operator")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Operator {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String contactEmail;
    private String contactPhone;
    private Double rating;
    private String image;
}