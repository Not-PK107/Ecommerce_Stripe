package com.example.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String paymentId;
    private String orderId;
    private String stripeSessionId;
    private String stripePaymentIntentId;
    private BigDecimal amount;
    private String currency;
    private String paymentStatus;
    private String createdAt;
    private String updatedAt;
}
