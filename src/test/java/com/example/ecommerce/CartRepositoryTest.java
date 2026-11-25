package com.example.ecommerce;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.repository.CartRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartRepositoryTest {

    private final CatalystClient catalystClient = mock(CatalystClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deleteItem_WhenRowExists_DeletesFromCatalyst() {
        String json = "{\"data\":[{\"ROWID\":\"row-1\",\"customerId\":\"cust-1\",\"productId\":\"prod-1\"}]}";
        when(catalystClient.getAllCartItems()).thenReturn(json);
        when(catalystClient.deleteCartItem("row-1")).thenReturn("{\"status\":\"success\"}");

        CartRepository repository = new CartRepository(catalystClient, objectMapper, true);
        boolean deleted = repository.deleteItem("cust-1", "prod-1");

        assertTrue(deleted);
        verify(catalystClient).deleteCartItem("row-1");
    }

    @Test
    void deleteItem_WhenDuplicateRowsExist_DeletesAllMatchingRows() {
        String json = "{\"data\":[" +
                "{\"ROWID\":\"row-1\",\"customerId\":\"cust-1\",\"productId\":\"prod-1\"}," +
                "{\"ROWID\":\"row-2\",\"customerId\":\"cust-1\",\"productId\":\"prod-1\"}," +
                "{\"ROWID\":\"row-3\",\"customerId\":\"cust-1\",\"productId\":\"prod-2\"}" +
                "]}";
        when(catalystClient.getAllCartItems()).thenReturn(json);

        CartRepository repository = new CartRepository(catalystClient, objectMapper, true);
        boolean deleted = repository.deleteItem("cust-1", "prod-1");

        assertTrue(deleted);
        verify(catalystClient).deleteCartItem("row-1");
        verify(catalystClient).deleteCartItem("row-2");
        verify(catalystClient, times(0)).deleteCartItem("row-3");
    }

    @Test
    void deleteItem_WhenNoMatchingRow_ReturnsFalse() {
        String json = "{\"data\":[{\"ROWID\":\"row-1\",\"customerId\":\"cust-2\",\"productId\":\"prod-1\"}]}";
        when(catalystClient.getAllCartItems()).thenReturn(json);

        CartRepository repository = new CartRepository(catalystClient, objectMapper, true);
        boolean deleted = repository.deleteItem("cust-1", "prod-1");

        assertFalse(deleted);
        verify(catalystClient, times(0)).deleteCartItem("row-1");
    }

    @Test
    void deleteByCustomerId_WhenRowsExist_DeletesAllRowsForCustomer() {
        String json = "{\"data\":[" +
                "{\"ROWID\":\"row-1\",\"customerId\":\"cust-1\",\"productId\":\"prod-1\"}," +
                "{\"ROWID\":\"row-2\",\"customerId\":\"cust-1\",\"productId\":\"prod-2\"}," +
                "{\"ROWID\":\"row-3\",\"customerId\":\"cust-2\",\"productId\":\"prod-3\"}" +
                "]}";
        when(catalystClient.getAllCartItems()).thenReturn(json);

        CartRepository repository = new CartRepository(catalystClient, objectMapper, true);
        boolean deleted = repository.deleteByCustomerId("cust-1");

        assertTrue(deleted);
        verify(catalystClient).deleteCartItem("row-1");
        verify(catalystClient).deleteCartItem("row-2");
        verify(catalystClient, times(0)).deleteCartItem("row-3");
    }
}
