package com.booking.bookingService.model;

import com.booking.bookingService.Enum.BusType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bus_model")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class BusModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    @JsonIgnoreProperties({ "buses", "routes", "trips" })
    private Operator operator;

    private String name;
    
    private int seatCapacity;
    private int totalDecks;
    private int gridRows;
    private int gridColumns;

    private boolean isLimousine;
    private boolean hasWC;

    @Enumerated(EnumType.STRING)
    private BusType type; // SEATER: Ghế ngồi, SLEEPER: Giường nằm, CABIN: Giường phòng

    // Seats are now defined at the Model level (Template)
    @OneToMany(mappedBy = "busModel", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("busModel")
    private List<Seat> seats;

    @OneToMany(mappedBy = "model")
    @JsonIgnoreProperties("busModel")
    private List<Bus> buses;

    @ElementCollection
    @CollectionTable(name = "bus_model_images", joinColumns = @JoinColumn(name = "bus_model_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();
    
    // Format: (Limousine) Giường phòng (có WC)
    public String getTypeDisplay() { 
        StringBuilder sb = new StringBuilder();

        // 1. Add "Limousine" prefix if true
        if (isLimousine) {
            sb.append("Limousine ");
        }

        // 2. Add the Vietnamese Display Name based on Enum
        // Assuming your BusType enum has a field like 'displayName' or 'name'
        if (type != null) {
            sb.append(type.getDisplayName());
        }

        // 3. Add (WC) suffix if true
        if (hasWC) {
            sb.append(" có WC");
        }

        sb.append(" ").append(seatCapacity).append(" chỗ");

        return sb.toString().trim(); 
    }
}