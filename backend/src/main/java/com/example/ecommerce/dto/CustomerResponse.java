package com.example.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {

    private String customerId;
    private String customerName;
    private String email;
    private String phone;
    private String address;
    private String createdAt;
}