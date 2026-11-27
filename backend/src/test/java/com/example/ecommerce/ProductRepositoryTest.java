package com.example.ecommerce;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRepositoryTest {

    private final CatalystDataStoreService dataStore = mock(CatalystDataStoreService.class);

    @Test
    void save_WhenCatalystWriteFails_ThrowsIllegalStateException() {
        when(dataStore.insertRow(eq("Product"), anyMap()))
                .thenThrow(new RuntimeException("Catalyst unreachable"));
        ProductRepository repository = new ProductRepository(dataStore, true);

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
        ProductRepository repository = new ProductRepository(dataStore, false);

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
        ZCRowObject inserted = ZCRowObject.getInstance();
        inserted.set("ROWID", 888L);
        inserted.set("prodName", "Catalyst Product");
        when(dataStore.insertRow(eq("Product"), anyMap())).thenReturn(inserted);

        ProductRepository repository = new ProductRepository(dataStore, true);

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
    void update_WhenCatalystEnabled_CallsUpdateRow() {
        ProductRepository repository = new ProductRepository(dataStore, true);

        Product product = Product.builder()
                .productId("888")
                .prodName("Updated Product")
                .prodDescription("Updated Description")
                .prodPrice(new BigDecimal("30.00"))
                .prodQuantity(5)
                .prodImage("https://images.unsplash.com/photo-1.jpg")
                .prodCategory("Ceramics")
                .build();

        when(dataStore.updateRow(eq("Product"), eq("888"), anyMap())).thenReturn(ZCRowObject.getInstance());

        Product updated = repository.update(product);

        assertEquals("888", updated.getProductId());
        assertEquals("Updated Product", updated.getProdName());
        verify(dataStore).updateRow(eq("Product"), eq("888"), argThat(fields ->
                "Updated Product".equals(fields.get("prodName"))
                        && Integer.valueOf(5).equals(fields.get("prodQuantity"))
                        && Boolean.TRUE.equals(fields.get("active"))));
    }

    @Test
    void findAllAndFindById_WhenCatalystEnabled_FilterInactiveProducts() {
        ZCRowObject activeRow = ZCRowObject.getInstance();
        activeRow.set("ROWID", 101L);
        activeRow.set("prodName", "Active Item");
        activeRow.set("prodPrice", "499.00");
        activeRow.set("prodQuantity", 4);
        activeRow.set("prodImage", "https://example.com/a.jpg");
        activeRow.set("prodCategory", "Tech");
        activeRow.set("active", true);

        ZCRowObject inactiveRow = ZCRowObject.getInstance();
        inactiveRow.set("ROWID", 102L);
        inactiveRow.set("prodName", "Inactive Item");
        inactiveRow.set("prodPrice", "199.00");
        inactiveRow.set("prodQuantity", 2);
        inactiveRow.set("active", false);

        when(dataStore.getAllRows("Product")).thenReturn(List.of(activeRow, inactiveRow));
        when(dataStore.getRowById("Product", "101")).thenReturn(Optional.of(activeRow));
        when(dataStore.getRowById("Product", "102")).thenReturn(Optional.of(inactiveRow));

        ProductRepository repository = new ProductRepository(dataStore, true);

        List<Product> products = repository.findAll();
        assertEquals(1, products.size());
        assertEquals("101", products.get(0).getProductId());
        assertNotNull(repository.findById("101"));
        assertNull(repository.findById("102"));
    }

    @Test
    void deleteById_WhenCatalystEnabled_SoftDeletesExistingProduct() {
        ZCRowObject row = ZCRowObject.getInstance();
        row.set("ROWID", 101L);
        row.set("prodName", "Active Item");
        row.set("active", true);

        when(dataStore.getRowById("Product", "101")).thenReturn(Optional.of(row));
        when(dataStore.getRowById("Product", "999")).thenReturn(Optional.empty());

        ProductRepository repository = new ProductRepository(dataStore, true);

        assertTrue(repository.deleteById("101"));
        verify(dataStore).updateRow(eq("Product"), eq("101"), argThat(fields ->
                Boolean.FALSE.equals(fields.get("active"))));
        assertFalse(repository.deleteById("999"));
    }
}
