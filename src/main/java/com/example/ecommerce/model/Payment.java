package com.example.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    private String paymentId;
    private String orderId;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private Long amount;
    private String currency;
    private String paymentStatus;
    private String createdAt;
    private String updatedAt;
}
