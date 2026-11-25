
package com.example.ecommerce.repository;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CartRepository {

    private static final Logger logger =
            LoggerFactory.getLogger(CartRepository.class);

    private final CatalystClient catalystClient;
    private final ObjectMapper objectMapper;
    private final boolean catalystEnabled;

    private final Map<String, Cart> localCarts =
            new ConcurrentHashMap<>();

    public CartRepository(
            CatalystClient catalystClient,
            ObjectMapper objectMapper,
            @Value("${app.catalyst.enabled:false}")
            boolean catalystEnabled) {

        this.catalystClient = catalystClient;
        this.objectMapper = objectMapper;
        this.catalystEnabled = catalystEnabled;
    }

    // Get or create cart
    public Cart getOrCreate(String customerId) {

        Cart cart = findByCustomerId(customerId);

        if (cart != null) {
            return cart;
        }

        return new Cart(
                customerId,
                new LinkedHashMap<>()
        );
    }

    // Find cart using customer ID
    public Cart findByCustomerId(String customerId) {

        if (!catalystEnabled) {
            return localCarts.get(customerId);
        }

        try {

            String response = catalystClient.getAllCartItems();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                throw new IllegalStateException(
                        "Invalid Catalyst Cartitem response"
                );
            }

            Cart cart = new Cart(
                    customerId,
                    new LinkedHashMap<>()
            );

            for (JsonNode row : data) {

                String rowCustomerId =
                        getString(row, "customerId");

                if (customerId.equals(rowCustomerId)) {

                    CartItem item = mapJsonToCartItem(row);

                    cart.getItems().put(
                            item.getProductId(),
                            item
                    );
                }
            }

            return cart;

        } catch (Exception ex) {
            throw catalystOperationFailed(
                    "find cart for customer " + customerId,
                    ex
            );
        }
    }

    // Save cart: INSERT new rows and UPDATE existing rows
    public Cart save(String customerId, Cart cart) {

        if (!catalystEnabled) {
            localCarts.put(customerId, cart);
            return cart;
        }

        try {

            for (CartItem item : cart.getItems().values()) {

                item.recalculateSubtotal();

                if (item.getRowId() == null
                        || item.getRowId().isBlank()) {

                    // New item: INSERT
                    String response =
                            catalystClient.insertCartItem(
                                    customerId,
                                    item
                            );

                    // Extract generated Catalyst ROWID
                    item.setRowId(extractRowId(response));

                } else {

                    // Existing item: UPDATE
                    catalystClient.updateCartItem(item);
                }
            }

            return cart;

        } catch (Exception ex) {
            throw catalystOperationFailed(
                    "save cart for customer " + customerId,
                    ex
            );
        }
    }

    // Delete one item from cart
    public boolean deleteItem(
            String customerId,
            String productId) {

        if (!catalystEnabled) {

            Cart cart = localCarts.get(customerId);

            return cart != null
                    && cart.getItems().remove(productId) != null;
        }

        try {

            String response = catalystClient.getAllCartItems();
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                return false;
            }

            List<String> rowIdsToDelete = new ArrayList<>();
            for (JsonNode row : data) {
                String rowCustId = getString(row, "customerId");
                String rowProdId = getString(row, "productId");
                String rowId = getString(row, "ROWID");

                if (customerId.equals(rowCustId)
                        && productId.equals(rowProdId)
                        && rowId != null
                        && !rowId.isBlank()) {
                    rowIdsToDelete.add(rowId);
                }
            }

            if (rowIdsToDelete.isEmpty()) {
                logger.info(
                        "No matching cart row found in Catalyst for customer {} and product {}",
                        customerId,
                        productId
                );
                return false;
            }

            for (String rowId : rowIdsToDelete) {
                catalystClient.deleteCartItem(rowId);
            }

            return true;

        } catch (Exception ex) {
            throw catalystOperationFailed(
                    "delete cart item for customer " + customerId,
                    ex
            );
        }
    }

    // Delete all items belonging to a customer
    public boolean deleteByCustomerId(String customerId) {

        if (!catalystEnabled) {
            return localCarts.remove(customerId) != null;
        }

        try {

            String response = catalystClient.getAllCartItems();
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (!data.isArray()) {
                return false;
            }

            boolean deletedAny = false;
            for (JsonNode row : data) {
                String rowCustId = getString(row, "customerId");
                String rowId = getString(row, "ROWID");

                if (customerId.equals(rowCustId)
                        && rowId != null
                        && !rowId.isBlank()) {
                    catalystClient.deleteCartItem(rowId);
                    deletedAny = true;
                }
            }

            return deletedAny;

        } catch (Exception ex) {
            throw catalystOperationFailed(
                    "delete cart for customer " + customerId,
                    ex
            );
        }
    }

    // Extract ROWID returned by Catalyst after insertion
    private String extractRowId(String response) throws Exception {

        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.path("data");

        if (data.isArray() && data.size() > 0) {

            String rowId = data.get(0)
                    .path("ROWID")
                    .asText();

            if (!rowId.isBlank()) {
                return rowId;
            }
        }

        throw new IllegalStateException(
                "Catalyst insert response did not contain ROWID: "
                        + response
        );
    }

    // Map Catalyst JSON into CartItem
    private CartItem mapJsonToCartItem(JsonNode row) {

        CartItem item = new CartItem();

        item.setRowId(getString(row, "ROWID"));
        item.setProductId(getString(row, "productId"));
        item.setProdName(getString(row, "prodName"));
        item.setProdPrice(getBigDecimal(row, "prodPrice"));
        item.setQuantity(row.path("quantity").asInt(0));
        item.setSubtotal(getBigDecimal(row, "subtotal"));

        return item;
    }

    private String getString(JsonNode row, String field) {

        JsonNode value = row.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private BigDecimal getBigDecimal(JsonNode row, String field) {

        JsonNode value = row.get(field);

        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value.asText());
    }

    private IllegalStateException catalystOperationFailed(
            String operation,
            Exception cause) {

        logger.error(
                "Failed to {} in Catalyst Cartitem table",
                operation,
                cause
        );

        return new IllegalStateException(
                "Failed to " + operation
                        + " in Catalyst Cartitem table",
                cause
        );
    }
}
