package com.example.ecommerce.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CustomerRequest {

    @NotBlank(message = "Customer name is required")
    @Pattern(
            regexp = "^[a-zA-Z\\s.'-]+$",
            message = "Name must not contain numbers and can only contain letters"
    )
    private String customerName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Pattern(
            regexp = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$",
            message = "Email must be in a valid format (e.g. name@example.com)"
    )
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(
            regexp = "^\\d{10}$",
            message = "Phone number must contain only numbers and be 10 digits"
    )
    private String phone;

    @NotBlank(message = "Address is required")
    private String address;
}