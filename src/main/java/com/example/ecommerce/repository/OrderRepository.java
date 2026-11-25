package com.example.ecommerce.repository;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(OrderRepository.class);
    private static final String ORDERS_TABLE = "Orders";
    private static final String ORDER_ITEMS_TABLE = "Orderitem";

    private final CatalystClient catalystClient;
    private final ObjectMapper objectMapper;

    public OrderRepository(CatalystClient catalystClient, ObjectMapper objectMapper) {
        this.catalystClient = catalystClient;
        this.objectMapper = objectMapper;
    }

    public Order insertOrder(Order order) {
        try {
            String response = catalystClient.insertRows(ORDERS_TABLE, List.of(orderRow(order)));
            JsonNode rows = responseRows(response, ORDERS_TABLE);
            if (rows.isEmpty() || !rows.get(0).hasNonNull("ROWID")) {
                throw new IllegalStateException("Catalyst order insert response did not contain ROWID");
            }
            order.setOrderId(rows.get(0).get("ROWID").asText());
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
            System.out.println("Order item payload: " + rows);
            String response = catalystClient.insertRows(ORDER_ITEMS_TABLE, rows);
            JsonNode inserted = responseRows(response, ORDER_ITEMS_TABLE);
            if (inserted.size() != items.size()) {
                throw new IllegalStateException("Catalyst did not confirm insertion of every order item");
            }
        } catch (Exception ex) {
            throw operationFailed("create order items", ex);
        }
    }

    public Order findById(String orderId) {
        List<Order> orders = readOrders();
        Order found = orders.stream()
                .filter(order -> orderId.equals(order.getOrderId()))
                .findFirst()
                .orElse(null);
        if (found != null) {
            found.setItems(readOrderItems(List.of(orderId)).getOrDefault(orderId, List.of()));
        }
        return found;
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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ROWID", Long.parseLong(order.getOrderId()));
            row.put("orderStatus", order.getOrderStatus());
            catalystClient.updateRows(ORDERS_TABLE, List.of(row));
            return order;
        } catch (Exception ex) {
            throw operationFailed("update order status", ex);
        }
    }

    public void deleteOrderAndItems(String orderId) {
        try {
            JsonNode rows = responseRows(catalystClient.getAllRows(ORDER_ITEMS_TABLE), ORDER_ITEMS_TABLE);
            for (JsonNode row : rows) {
                if (orderId.equals(text(row, "orderId"))) {
                    String rowId = text(row, "ROWID");
                    if (rowId == null) {
                        throw new IllegalStateException("Catalyst order item did not include ROWID during cleanup");
                    }
                    catalystClient.deleteRow(ORDER_ITEMS_TABLE, rowId);
                }
            }
            catalystClient.deleteRow(ORDERS_TABLE, orderId);
        } catch (Exception ex) {
            throw operationFailed("compensate failed order creation", ex);
        }
    }

    private List<Order> readOrders() {
        try {
            JsonNode rows = responseRows(catalystClient.getAllRows(ORDERS_TABLE), ORDERS_TABLE);
            List<Order> orders = new ArrayList<>();
            for (JsonNode row : rows) {
                orders.add(Order.builder()
                        .orderId(text(row, "ROWID"))
                        .customerId(text(row, "customerId"))
                        .totalAmount(decimal(row, "totalAmount"))
                        .orderStatus(text(row, "orderStatus"))
                        .orderDate(text(row, "orderDate"))
                        .build());
            }
            return orders;
        } catch (Exception ex) {
            throw operationFailed("list orders", ex);
        }
    }

    private Map<String, List<OrderItem>> readOrderItems(List<String> orderIds) {
        try {
            JsonNode rows = responseRows(catalystClient.getAllRows(ORDER_ITEMS_TABLE), ORDER_ITEMS_TABLE);
            Map<String, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
            for (String orderId : orderIds) {
                itemsByOrder.put(orderId, new ArrayList<>());
            }
            for (JsonNode row : rows) {
                String orderId = text(row, "orderId");
                List<OrderItem> items = itemsByOrder.get(orderId);
                if (items != null) {
                    items.add(OrderItem.builder()
                            .orderId(orderId)
                            .productId(text(row, "productId"))
                            .prodName(text(row, "prodName"))
                            .prodPrice(decimal(row, "prodPrice"))
                            .quantity(row.path("quantity").asInt())
                            .subtotal(decimal(row, "subtotal"))
                            .build());
                }
            }
            return itemsByOrder;
        } catch (Exception ex) {
            throw operationFailed("list order items", ex);
        }
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

    private JsonNode responseRows(String response, String tableName) throws Exception {
        JsonNode rows = objectMapper.readTree(response).path("data");
        if (!rows.isArray()) {
            throw new IllegalStateException("Invalid Catalyst response for table '" + tableName + "'");
        }
        return rows;
    }

    private String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private BigDecimal decimal(JsonNode row, String field) {
        String value = text(row, field);
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private IllegalStateException operationFailed(String operation, Exception cause) {
        logger.error("Catalyst {} operation failed", operation, cause);
        return new IllegalStateException("Catalyst order operation failed", cause);
    }
}
