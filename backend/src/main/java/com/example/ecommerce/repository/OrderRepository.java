package com.example.ecommerce.repository;

import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);
    private static final String ORDERS_TABLE = "Orders";
    private static final String ORDER_ITEMS_TABLE = "Orderitem";

    private final CatalystDataStoreService dataStore;

    public OrderRepository(CatalystDataStoreService dataStore) {
        this.dataStore = dataStore;
    }

    public Order insertOrder(Order order) {
        try {
            ZCRowObject inserted = dataStore.insertRow(ORDERS_TABLE, orderRow(order));
            String rowId = CatalystDataStoreService.getRequiredRowId(inserted, ORDERS_TABLE);
            order.setOrderId(rowId);
            return order;
        } catch (Exception ex) {
            throw operationFailed("create order", ex);
        }
    }

    public void insertOrderItems(List<OrderItem> items) {
        if (items.isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> rows = items.stream().map(this::orderItemRow).toList();
            List<ZCRowObject> inserted = dataStore.insertRows(ORDER_ITEMS_TABLE, rows);
            if (inserted.size() != items.size()) {
                throw new IllegalStateException("Catalyst did not confirm insertion of every order item");
            }
        } catch (Exception ex) {
            throw operationFailed("create order items", ex);
        }
    }

    public Order findById(String orderId) {
        try {
            Optional<ZCRowObject> row = dataStore.getRowById(ORDERS_TABLE, orderId);
            if (row.isEmpty()) {
                return null;
            }
            Order found = toOrder(row.get());
            found.setItems(readOrderItems(List.of(found.getOrderId())).getOrDefault(found.getOrderId(), List.of()));
            return found;
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException ise && "Catalyst order operation failed".equals(ise.getMessage())) {
                throw ise;
            }
            throw operationFailed("find order", ex);
        }
    }

    public List<Order> findByCustomerId(String customerId) {
        List<Order> orders = readOrders().stream()
                .filter(order -> customerId.equals(order.getCustomerId()))
                .toList();
        if (orders.isEmpty()) {
            return orders;
        }
        Map<String, List<OrderItem>> itemsByOrder = readOrderItems(
                orders.stream().map(Order::getOrderId).toList());
        orders.forEach(order -> order.setItems(itemsByOrder.getOrDefault(order.getOrderId(), List.of())));
        return orders;
    }

    public Order updateStatus(Order order) {
        try {
            dataStore.updateRow(ORDERS_TABLE, order.getOrderId(), Map.of("orderStatus", order.getOrderStatus()));
            return order;
        } catch (Exception ex) {
            throw operationFailed("update order status", ex);
        }
    }

    public void deleteOrderAndItems(String orderId) {
        try {
            List<ZCRowObject> rows = dataStore.getAllRows(ORDER_ITEMS_TABLE);
            for (ZCRowObject row : rows) {
                if (orderId.equals(CatalystDataStoreService.getString(row, "orderId"))) {
                    String rowId = CatalystDataStoreService.getString(row, "ROWID");
                    if (rowId == null || rowId.isBlank()) {
                        throw new IllegalStateException("Catalyst order item did not include ROWID during cleanup");
                    }
                    dataStore.deleteRow(ORDER_ITEMS_TABLE, rowId);
                }
            }
            dataStore.deleteRow(ORDERS_TABLE, orderId);
        } catch (Exception ex) {
            throw operationFailed("compensate failed order creation", ex);
        }
    }

    private List<Order> readOrders() {
        try {
            List<ZCRowObject> rows = dataStore.getAllRows(ORDERS_TABLE);
            List<Order> orders = new ArrayList<>();
            for (ZCRowObject row : rows) {
                orders.add(toOrder(row));
            }
            return orders;
        } catch (Exception ex) {
            throw operationFailed("list orders", ex);
        }
    }

    private Map<String, List<OrderItem>> readOrderItems(List<String> orderIds) {
        try {
            List<ZCRowObject> rows = dataStore.getAllRows(ORDER_ITEMS_TABLE);
            Map<String, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
            for (String orderId : orderIds) {
                itemsByOrder.put(orderId, new ArrayList<>());
            }
            for (ZCRowObject row : rows) {
                String orderId = CatalystDataStoreService.getString(row, "orderId");
                List<OrderItem> items = itemsByOrder.get(orderId);
                if (items != null) {
                    items.add(OrderItem.builder()
                            .orderId(orderId)
                            .productId(CatalystDataStoreService.getString(row, "productId"))
                            .prodName(CatalystDataStoreService.getString(row, "prodName"))
                            .prodPrice(CatalystDataStoreService.getBigDecimal(row, "prodPrice"))
                            .quantity(CatalystDataStoreService.getInt(row, "quantity", 0))
                            .subtotal(CatalystDataStoreService.getBigDecimal(row, "subtotal"))
                            .build());
                }
            }
            return itemsByOrder;
        } catch (Exception ex) {
            throw operationFailed("list order items", ex);
        }
    }

    private Order toOrder(ZCRowObject row) {
        return Order.builder()
                .orderId(CatalystDataStoreService.getString(row, "ROWID"))
                .customerId(CatalystDataStoreService.getString(row, "customerId"))
                .totalAmount(CatalystDataStoreService.getBigDecimal(row, "totalAmount"))
                .orderStatus(CatalystDataStoreService.getString(row, "orderStatus"))
                .orderDate(CatalystDataStoreService.getString(row, "orderDate"))
                .build();
    }

    private Map<String, Object> orderRow(Order order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customerId", order.getCustomerId());
        row.put("totalAmount", order.getTotalAmount());
        row.put("orderStatus", order.getOrderStatus());
        row.put("orderDate", order.getOrderDate());
        return row;
    }

    private Map<String, Object> orderItemRow(OrderItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", item.getOrderId());
        row.put("productId", item.getProductId());
        row.put("prodName", item.getProdName());
        row.put("prodPrice", item.getProdPrice());
        row.put("quantity", item.getQuantity());
        row.put("subtotal", item.getSubtotal());
        return row;
    }

    private IllegalStateException operationFailed(String operation, Exception cause) {
        logger.error("Catalyst {} operation failed", operation, cause);
        return new IllegalStateException("Catalyst order operation failed", cause);
    }
}
