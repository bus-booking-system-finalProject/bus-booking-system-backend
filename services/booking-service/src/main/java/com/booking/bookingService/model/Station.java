package com.booking.bookingService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "station")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Station {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    @JsonIgnoreProperties({ "buses", "routes", "trips" })
    private Operator operator;

    private String name; // e.g. "Văn phòng Quận 1"
    private String address; // e.g. "123 Nguyễn Huệ"
    private String ward;
    private String city;
}