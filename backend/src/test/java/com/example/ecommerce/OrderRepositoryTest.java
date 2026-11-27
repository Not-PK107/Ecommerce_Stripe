package com.example.ecommerce;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderRepositoryTest {

    private final CatalystDataStoreService dataStore = mock(CatalystDataStoreService.class);
    private final OrderRepository repository = new OrderRepository(dataStore);

    @Test
    void insertOrderAndInsertOrderItems_AssignsOrderIdAndPersistsItems() {
        ZCRowObject insertedOrder = ZCRowObject.getInstance();
        insertedOrder.set("ROWID", 5001L);
        when(dataStore.insertRow(eq("Orders"), anyMap())).thenReturn(insertedOrder);

        ZCRowObject itemRow = ZCRowObject.getInstance();
        itemRow.set("ROWID", 6001L);
        when(dataStore.insertRows(eq("Orderitem"), anyList())).thenReturn(List.of(itemRow));

        Order order = Order.builder()
                .customerId("cust-1")
                .totalAmount(new BigDecimal("199.00"))
                .orderStatus("PENDING_PAYMENT")
                .orderDate("2026-11-25 10:00:00")
                .build();

        Order saved = repository.insertOrder(order);
        assertEquals("5001", saved.getOrderId());

        OrderItem item = OrderItem.builder()
                .orderId("5001")
                .productId("prod-1")
                .prodName("Keyboard")
                .prodPrice(new BigDecimal("199.00"))
                .quantity(1)
                .subtotal(new BigDecimal("199.00"))
                .build();

        repository.insertOrderItems(List.of(item));
        verify(dataStore).insertRows(eq("Orderitem"), argThat(rows ->
                rows.size() == 1 && "5001".equals(rows.get(0).get("orderId"))));
    }

    @Test
    void insertOrderItems_WhenCountMismatch_ThrowsIllegalStateException() {
        when(dataStore.insertRows(eq("Orderitem"), anyList())).thenReturn(List.of());

        OrderItem item = OrderItem.builder()
                .orderId("5001")
                .productId("prod-1")
                .prodName("Keyboard")
                .prodPrice(new BigDecimal("199.00"))
                .quantity(1)
                .subtotal(new BigDecimal("199.00"))
                .build();

        assertThrows(IllegalStateException.class, () -> repository.insertOrderItems(List.of(item)));
    }

    @Test
    void findByIdAndFindByCustomerId_LoadsMatchingItems() {
        ZCRowObject orderRow = ZCRowObject.getInstance();
        orderRow.set("ROWID", 5001L);
        orderRow.set("customerId", "cust-1");
        orderRow.set("totalAmount", "200.00");
        orderRow.set("orderStatus", "PAID");
        orderRow.set("orderDate", "2026-11-25 10:00:00");

        ZCRowObject otherOrderRow = ZCRowObject.getInstance();
        otherOrderRow.set("ROWID", 5002L);
        otherOrderRow.set("customerId", "cust-2");
        otherOrderRow.set("totalAmount", "50.00");
        otherOrderRow.set("orderStatus", "PENDING_PAYMENT");

        ZCRowObject orderItemRow = ZCRowObject.getInstance();
        orderItemRow.set("ROWID", 6001L);
        orderItemRow.set("orderId", "5001");
        orderItemRow.set("productId", "prod-1");
        orderItemRow.set("prodName", "Keyboard");
        orderItemRow.set("prodPrice", "100.00");
        orderItemRow.set("quantity", 2);
        orderItemRow.set("subtotal", "200.00");

        when(dataStore.getRowById("Orders", "5001")).thenReturn(Optional.of(orderRow));
        when(dataStore.getRowById("Orders", "missing")).thenReturn(Optional.empty());
        when(dataStore.getAllRows("Orders")).thenReturn(List.of(orderRow, otherOrderRow));
        when(dataStore.getAllRows("Orderitem")).thenReturn(List.of(orderItemRow));

        Order found = repository.findById("5001");
        assertNotNull(found);
        assertEquals(1, found.getItems().size());
        assertEquals("Keyboard", found.getItems().get(0).getProdName());
        assertNull(repository.findById("missing"));

        List<Order> customerOrders = repository.findByCustomerId("cust-1");
        assertEquals(1, customerOrders.size());
        assertEquals("5001", customerOrders.get(0).getOrderId());
        assertEquals(1, customerOrders.get(0).getItems().size());
    }

    @Test
    void updateStatusAndDeleteOrderAndItems_UpdatesAndCompensates() {
        Order order = Order.builder().orderId("5001").orderStatus("PAID").build();
        repository.updateStatus(order);
        verify(dataStore).updateRow(eq("Orders"), eq("5001"), argThat(fields ->
                "PAID".equals(fields.get("orderStatus"))));

        ZCRowObject item1 = ZCRowObject.getInstance();
        item1.set("ROWID", 6001L);
        item1.set("orderId", "5001");
        ZCRowObject item2 = ZCRowObject.getInstance();
        item2.set("ROWID", 6002L);
        item2.set("orderId", "5002");
        when(dataStore.getAllRows("Orderitem")).thenReturn(List.of(item1, item2));

        repository.deleteOrderAndItems("5001");
        verify(dataStore).deleteRow("Orderitem", "6001");
        verify(dataStore).deleteRow("Orders", "5001");
    }
}
