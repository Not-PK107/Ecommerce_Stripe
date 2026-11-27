package com.example.ecommerce;

import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartRepositoryTest {

    private final CatalystDataStoreService dataStore = mock(CatalystDataStoreService.class);

    @Test
    void deleteItem_WhenRowExists_DeletesFromCatalyst() {
        ZCRowObject row1 = cartRow("1001", "cust-1", "prod-1", "Phone", "500.00", 1);
        when(dataStore.getAllRows("Cartitem")).thenReturn(List.of(row1));

        CartRepository repository = new CartRepository(dataStore, true);
        boolean deleted = repository.deleteItem("cust-1", "prod-1");

        assertTrue(deleted);
        verify(dataStore).deleteRow("Cartitem", "1001");
    }

    @Test
    void deleteItem_WhenDuplicateRowsExist_DeletesAllMatchingRows() {
        ZCRowObject row1 = cartRow("1001", "cust-1", "prod-1", "Phone", "500.00", 1);
        ZCRowObject row2 = cartRow("1002", "cust-1", "prod-1", "Phone", "500.00", 2);
        ZCRowObject row3 = cartRow("1003", "cust-1", "prod-2", "Case", "50.00", 1);
        when(dataStore.getAllRows("Cartitem")).thenReturn(List.of(row1, row2, row3));

        CartRepository repository = new CartRepository(dataStore, true);
        boolean deleted = repository.deleteItem("cust-1", "prod-1");

        assertTrue(deleted);
        verify(dataStore).deleteRow("Cartitem", "1001");
        verify(dataStore).deleteRow("Cartitem", "1002");
        verify(dataStore, times(0)).deleteRow("Cartitem", "1003");
    }

    @Test
    void deleteItem_WhenNoMatchingRow_ReturnsFalse() {
        ZCRowObject row1 = cartRow("1001", "cust-2", "prod-1", "Phone", "500.00", 1);
        when(dataStore.getAllRows("Cartitem")).thenReturn(List.of(row1));

        CartRepository repository = new CartRepository(dataStore, true);
        boolean deleted = repository.deleteItem("cust-1", "prod-1");

        assertFalse(deleted);
        verify(dataStore, times(0)).deleteRow("Cartitem", "1001");
    }

    @Test
    void deleteByCustomerId_WhenRowsExist_DeletesAllRowsForCustomer() {
        ZCRowObject row1 = cartRow("1001", "cust-1", "prod-1", "Phone", "500.00", 1);
        ZCRowObject row2 = cartRow("1002", "cust-1", "prod-2", "Case", "50.00", 1);
        ZCRowObject row3 = cartRow("1003", "cust-2", "prod-3", "Cable", "20.00", 1);
        when(dataStore.getAllRows("Cartitem")).thenReturn(List.of(row1, row2, row3));

        CartRepository repository = new CartRepository(dataStore, true);
        boolean deleted = repository.deleteByCustomerId("cust-1");

        assertTrue(deleted);
        verify(dataStore).deleteRow("Cartitem", "1001");
        verify(dataStore).deleteRow("Cartitem", "1002");
        verify(dataStore, times(0)).deleteRow("Cartitem", "1003");
    }

    @Test
    void saveAndFindByCustomerId_InsertsNewItemsAndUpdatesExistingItems() {
        ZCRowObject insertedRow = ZCRowObject.getInstance();
        insertedRow.set("ROWID", 2001L);
        when(dataStore.insertRow(eq("Cartitem"), anyMap())).thenReturn(insertedRow);
        when(dataStore.updateRow(eq("Cartitem"), eq("2002"), anyMap())).thenReturn(ZCRowObject.getInstance());

        CartItem newItem = CartItem.builder()
                .productId("prod-1")
                .prodName("Phone")
                .prodPrice(new BigDecimal("100.00"))
                .quantity(2)
                .build();
        CartItem existingItem = CartItem.builder()
                .rowId("2002")
                .productId("prod-2")
                .prodName("Case")
                .prodPrice(new BigDecimal("25.00"))
                .quantity(3)
                .build();

        Cart cart = new Cart("cust-1", new LinkedHashMap<>());
        cart.addItem(newItem);
        cart.addItem(existingItem);

        CartRepository repository = new CartRepository(dataStore, true);
        Cart saved = repository.save("cust-1", cart);

        assertEquals("2001", saved.getItems().get("prod-1").getRowId());
        assertEquals(new BigDecimal("200.00"), saved.getItems().get("prod-1").getSubtotal());
        verify(dataStore).insertRow(eq("Cartitem"), argThat(fields ->
                "cust-1".equals(fields.get("customerId")) && "prod-1".equals(fields.get("productId"))));
        verify(dataStore).updateRow(eq("Cartitem"), eq("2002"), argThat(fields ->
                "prod-2".equals(fields.get("productId"))
                        && Integer.valueOf(3).equals(fields.get("quantity"))));

        ZCRowObject stored = cartRow("2001", "cust-1", "prod-1", "Phone", "100.00", 2);
        when(dataStore.getAllRows("Cartitem")).thenReturn(List.of(stored));
        Cart loaded = repository.findByCustomerId("cust-1");
        assertEquals(1, loaded.getItems().size());
        assertEquals("2001", loaded.getItems().get("prod-1").getRowId());
    }

    private static ZCRowObject cartRow(
            String rowId,
            String customerId,
            String productId,
            String prodName,
            String prodPrice,
            int quantity) {
        ZCRowObject row = ZCRowObject.getInstance();
        row.set("ROWID", rowId);
        row.set("customerId", customerId);
        row.set("productId", productId);
        row.set("prodName", prodName);
        row.set("prodPrice", prodPrice);
        row.set("quantity", quantity);
        row.set("subtotal", new BigDecimal(prodPrice).multiply(BigDecimal.valueOf(quantity)).toPlainString());
        return row;
    }
}
