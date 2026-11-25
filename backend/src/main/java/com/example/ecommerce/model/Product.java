package com.example.ecommerce.model;

import lombok.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    private String productId;

    @NotBlank(message = "Product name cannot be empty")
    private String prodName;

    @NotBlank(message = "Description cannot be empty")
    private String prodDescription;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be greater than zero")
    private BigDecimal prodPrice;

    @PositiveOrZero(message = "Quantity cannot be negative")
    private int prodQuantity;

    @NotBlank(message = "Product image URL is required")
    private String prodImage;

    @NotBlank(message = "Category cannot be empty")
    private String prodCategory;

    @Builder.Default
    private boolean active = true;
}