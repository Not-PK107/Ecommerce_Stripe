package com.example.ecommerce.repository;

import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CartRepository {

    private static final Logger logger = LoggerFactory.getLogger(CartRepository.class);
    private static final String TABLE_NAME = "Cartitem";

    private final CatalystDataStoreService dataStore;
    private final boolean catalystEnabled;

    private final Map<String, Cart> localCarts = new ConcurrentHashMap<>();

    public CartRepository(
            CatalystDataStoreService dataStore,
            @Value("${app.catalyst.enabled:false}") boolean catalystEnabled) {

        this.dataStore = dataStore;
        this.catalystEnabled = catalystEnabled;
    }

    public Cart getOrCreate(String customerId) {
        Cart cart = findByCustomerId(customerId);
        if (cart != null) {
            return cart;
        }
        return new Cart(customerId, new LinkedHashMap<>());
    }

    public Cart findByCustomerId(String customerId) {
        if (!catalystEnabled) {
            return localCarts.get(customerId);
        }

        try {
            List<ZCRowObject> rows = dataStore.getAllRows(TABLE_NAME);
            Cart cart = new Cart(customerId, new LinkedHashMap<>());
            for (ZCRowObject row : rows) {
                String rowCustomerId = CatalystDataStoreService.getString(row, "customerId");
                if (customerId.equals(rowCustomerId)) {
                    CartItem item = mapRowToCartItem(row);
                    cart.getItems().put(item.getProductId(), item);
                }
            }
            return cart;
        } catch (Exception ex) {
            throw catalystOperationFailed("find cart for customer " + customerId, ex);
        }
    }

    public Cart save(String customerId, Cart cart) {
        if (!catalystEnabled) {
            localCarts.put(customerId, cart);
            return cart;
        }

        try {
            for (CartItem item : cart.getItems().values()) {
                item.recalculateSubtotal();
                if (item.getRowId() == null || item.getRowId().isBlank()) {
                    ZCRowObject inserted = dataStore.insertRow(TABLE_NAME, toInsertFields(customerId, item));
                    item.setRowId(CatalystDataStoreService.getRequiredRowId(inserted, TABLE_NAME));
                } else {
                    dataStore.updateRow(TABLE_NAME, item.getRowId(), toUpdateFields(item));
                }
            }
            return cart;
        } catch (Exception ex) {
            throw catalystOperationFailed("save cart for customer " + customerId, ex);
        }
    }

    public boolean deleteItem(String customerId, String productId) {
        if (!catalystEnabled) {
            Cart cart = localCarts.get(customerId);
            return cart != null && cart.getItems().remove(productId) != null;
        }

        try {
            List<ZCRowObject> rows = dataStore.getAllRows(TABLE_NAME);
            List<String> rowIdsToDelete = new ArrayList<>();
            for (ZCRowObject row : rows) {
                String rowCustId = CatalystDataStoreService.getString(row, "customerId");
                String rowProdId = CatalystDataStoreService.getString(row, "productId");
                String rowId = CatalystDataStoreService.getString(row, "ROWID");

                if (customerId.equals(rowCustId)
                        && productId.equals(rowProdId)
                        && rowId != null
                        && !rowId.isBlank()) {
                    rowIdsToDelete.add(rowId);
                }
            }

            if (rowIdsToDelete.isEmpty()) {
                logger.info("No matching cart row found in Catalyst for customer {} and product {}",
                        customerId, productId);
                return false;
            }

            for (String rowId : rowIdsToDelete) {
                dataStore.deleteRow(TABLE_NAME, rowId);
            }
            return true;
        } catch (Exception ex) {
            throw catalystOperationFailed("delete cart item for customer " + customerId, ex);
        }
    }

    public boolean deleteByCustomerId(String customerId) {
        if (!catalystEnabled) {
            return localCarts.remove(customerId) != null;
        }

        try {
            List<ZCRowObject> rows = dataStore.getAllRows(TABLE_NAME);
            boolean deletedAny = false;
            for (ZCRowObject row : rows) {
                String rowCustId = CatalystDataStoreService.getString(row, "customerId");
                String rowId = CatalystDataStoreService.getString(row, "ROWID");

                if (customerId.equals(rowCustId) && rowId != null && !rowId.isBlank()) {
                    dataStore.deleteRow(TABLE_NAME, rowId);
                    deletedAny = true;
                }
            }
            return deletedAny;
        } catch (Exception ex) {
            throw catalystOperationFailed("delete cart for customer " + customerId, ex);
        }
    }

    private Map<String, Object> toInsertFields(String customerId, CartItem item) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("customerId", customerId);
        fields.put("productId", item.getProductId());
        fields.put("prodName", item.getProdName());
        fields.put("prodPrice", item.getProdPrice());
        fields.put("quantity", item.getQuantity());
        fields.put("subtotal", item.getSubtotal());
        return fields;
    }

    private Map<String, Object> toUpdateFields(CartItem item) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("productId", item.getProductId());
        fields.put("prodName", item.getProdName());
        fields.put("prodPrice", item.getProdPrice());
        fields.put("quantity", item.getQuantity());
        fields.put("subtotal", item.getSubtotal());
        return fields;
    }

    private CartItem mapRowToCartItem(ZCRowObject row) {
        CartItem item = new CartItem();
        item.setRowId(CatalystDataStoreService.getString(row, "ROWID"));
        item.setProductId(CatalystDataStoreService.getString(row, "productId"));
        item.setProdName(CatalystDataStoreService.getString(row, "prodName"));
        item.setProdPrice(CatalystDataStoreService.getBigDecimal(row, "prodPrice"));
        item.setQuantity(CatalystDataStoreService.getInt(row, "quantity", 0));
        item.setSubtotal(CatalystDataStoreService.getBigDecimal(row, "subtotal"));
        return item;
    }

    private IllegalStateException catalystOperationFailed(String operation, Exception cause) {
        logger.error("Failed to {} in Catalyst Cartitem table", operation, cause);
        return new IllegalStateException("Failed to " + operation + " in Catalyst Cartitem table", cause);
    }
}
