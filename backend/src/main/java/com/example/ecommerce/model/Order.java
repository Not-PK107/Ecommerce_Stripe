package com.example.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    private String orderId;
    private String customerId;
    private BigDecimal totalAmount;
    private String orderStatus;
    private String orderDate;

    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
