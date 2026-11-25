package com.example.ecommerce;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @Test
    void deductStock_ReducesQuantityCorrectly() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductService service = new ProductService(repository);

        Product product = Product.builder()
                .productId("prod-1")
                .prodName("iPhone 16 Pro Max")
                .prodPrice(new BigDecimal("90000.00"))
                .prodQuantity(11)
                .active(true)
                .build();

        when(repository.findById("prod-1")).thenReturn(product);
        when(repository.update(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = service.deductStock("prod-1", 1);

        assertEquals(10, updated.getProdQuantity());
        verify(repository).update(product);
    }

    @Test
    void deductStock_DoesNotDropBelowZero() {
        ProductRepository repository = mock(ProductRepository.class);
        ProductService service = new ProductService(repository);

        Product product = Product.builder()
                .productId("prod-1")
                .prodName("iPhone 16 Pro Max")
                .prodPrice(new BigDecimal("90000.00"))
                .prodQuantity(2)
                .active(true)
                .build();

        when(repository.findById("prod-1")).thenReturn(product);
        when(repository.update(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = service.deductStock("prod-1", 5);

        assertEquals(0, updated.getProdQuantity());
        verify(repository).update(product);
    }
}
