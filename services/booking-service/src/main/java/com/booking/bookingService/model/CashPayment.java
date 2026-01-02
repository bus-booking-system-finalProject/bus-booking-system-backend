package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import com.booking.bookingService.Enum.PaymentMethod;

@Entity
@DiscriminatorValue("CASH")
@Data @NoArgsConstructor @EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CashPayment extends Payment {
    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.CASH;
    }
}