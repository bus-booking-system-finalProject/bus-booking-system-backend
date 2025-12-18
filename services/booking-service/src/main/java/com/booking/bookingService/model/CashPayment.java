package com.booking.bookingService.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import com.booking.bookingService.Enum.PaymentMethod;

@Entity
@DiscriminatorValue("CASH")
@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CashPayment extends Payment {

    @Column(name = "cashier_name")
    private String cashierName; // Specific to Cash

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.CASH;
    }
}