package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "station")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Station {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private Operator operator;

    private String name;    // e.g. "Văn phòng Quận 1"
    private String address; // e.g. "123 Nguyễn Huệ"
    private String ward;
    private String city;
}