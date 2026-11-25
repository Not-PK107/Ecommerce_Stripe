
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
public class CartItem {

    private String rowId;

    private String productId;

    private String prodName;

    private BigDecimal prodPrice;

    private int quantity;

    private BigDecimal subtotal;

    public void recalculateSubtotal() {

        if (prodPrice == null) {
            subtotal = BigDecimal.ZERO;
            return;
        }

        subtotal = prodPrice.multiply(
                BigDecimal.valueOf(quantity)
        );
    }
}
