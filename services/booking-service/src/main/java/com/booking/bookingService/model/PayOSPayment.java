package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import com.booking.bookingService.Enum.PaymentMethod;

@Entity
@DiscriminatorValue("PAYOS") // Maps to 'payment_method' column
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class PayOSPayment extends Payment {

    // Specific to PayOS (The numeric ID required by their API)
    @Column(name = "payos_order_code", unique = true)
    private Long orderCode; 

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.PAYOS;
    }
}