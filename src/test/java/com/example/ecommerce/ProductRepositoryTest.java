package com.example.ecommerce;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductRepositoryTest {

    private final CatalystClient catalystClient = mock(CatalystClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void save_WhenCatalystWriteFails_ThrowsIllegalStateException() {
        when(catalystClient.insertProduct(any())).thenThrow(new RuntimeException("Catalyst unreachable"));
        ProductRepository repository = new ProductRepository(catalystClient, objectMapper, true);

        Product product = Product.builder()
                .prodName("Test product")
                .prodDescription("Catalyst write failure test")
                .prodPrice(new BigDecimal("1.23"))
                .prodQuantity(1)
                .prodImage("https://example.com/product.jpg")
                .prodCategory("Test")
                .build();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository.save(product));

        assertTrue(exception.getMessage().contains("save product"));
    }

    @Test
    void save_WhenCatalystDisabled_SavesLocallyAndGeneratesId() {
        ProductRepository repository = new ProductRepository(catalystClient, objectMapper, false);

        Product product = Product.builder()
                .prodName("Local Product")
                .prodDescription("Local product description")
                .prodPrice(new BigDecimal("10.00"))
                .prodQuantity(5)
                .prodImage("https://example.com/product.jpg")
                .prodCategory("Test")
                .build();

        Product saved = repository.save(product);

        assertNotNull(saved.getProductId());
        assertEquals("Local Product", repository.findById(saved.getProductId()).getProdName());
    }

    @Test
    void save_WhenCatalystEnabled_AssignsRowIdAsProductId() {
        when(catalystClient.insertProduct(any()))
                .thenReturn("{\"data\":[{\"ROWID\":\"888\",\"prodName\":\"Catalyst Product\"}]}");
        ProductRepository repository = new ProductRepository(catalystClient, objectMapper, true);

        Product product = Product.builder()
                .prodName("Catalyst Product")
                .prodDescription("Description")
                .prodPrice(new BigDecimal("25.00"))
                .prodQuantity(2)
                .prodImage("https://example.com/product.jpg")
                .prodCategory("Test")
                .build();

        Product saved = repository.save(product);

        assertEquals("888", saved.getProductId());
    }

    @Test
    void update_WhenCatalystEnabled_CallsUpdateProduct() {
        ProductRepository repository = new ProductRepository(catalystClient, objectMapper, true);

        Product product = Product.builder()
                .productId("888")
                .prodName("Updated Product")
                .prodDescription("Updated Description")
                .prodPrice(new BigDecimal("30.00"))
                .prodQuantity(5)
                .prodImage("https://images.unsplash.com/photo-1.jpg")
                .prodCategory("Ceramics")
                .build();

        when(catalystClient.updateProduct(product)).thenReturn("{\"data\":[]}");

        Product updated = repository.update(product);

        assertEquals("888", updated.getProductId());
        assertEquals("Updated Product", updated.getProdName());
    }
}

