
package com.example.ecommerce.config;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.model.CartItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CatalystClient {

    private static final Logger logger =
            LoggerFactory.getLogger(CatalystClient.class);

    private final OAuthManager oAuthManager;
    private final RestClient restClient = RestClient.create();

    @Value("${zoho.catalyst.project-id}")
    private String projectId;

    @Value("${zoho.catalyst.project-domain:https://api.catalyst.zoho.in}")
    private String baseUrl;

    @Value("${zoho.catalyst.environment:Development}")
    private String environment;

    public CatalystClient(OAuthManager oAuthManager) {
        this.oAuthManager = oAuthManager;
    }

    private String getAccessToken() {
        return oAuthManager.generateAccessToken();
    }

    private String getTableUrl(String tableName) {
        return baseUrl + "/baas/v1/project/"
                + projectId + "/table/" + tableName + "/row";
    }

    public String getAllRows(String tableName) {
        return authenticatedRequest(restClient.get().uri(getTableUrl(tableName)))
                .retrieve()
                .body(String.class);
    }

    public String insertRows(String tableName, List<Map<String, Object>> rows) {
        return authenticatedRequest(restClient.post()
                .uri(getTableUrl(tableName))
                .contentType(MediaType.APPLICATION_JSON)
                .body(rows))
                .retrieve()
                .body(String.class);
    }

    public String updateRows(String tableName, List<Map<String, Object>> rows) {
        return authenticatedRequest(restClient.put()
                .uri(getTableUrl(tableName))
                .contentType(MediaType.APPLICATION_JSON)
                .body(rows))
                .retrieve()
                .body(String.class);
    }

    public String deleteRow(String tableName, String rowId) {
        if (rowId == null || rowId.isBlank()) {
            throw new IllegalArgumentException("Row ID is required for deleting a row");
        }
        try {
            return authenticatedRequest(restClient.delete()
                    .uri(getTableUrl(tableName) + "/" + rowId))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody != null && (responseBody.contains("INVALID_ID") || responseBody.contains("No such row"))) {
                logger.info("Row {} in table {} is already absent or deleted in Catalyst (404 INVALID_ID)", rowId, tableName);
                return null;
            }
            throw ex;
        }
    }

    private RestClient.RequestHeadersSpec<?> authenticatedRequest(
            RestClient.RequestHeadersSpec<?> request) {

        return request
                .header("Authorization",
                        "Zoho-oauthtoken " + getAccessToken())
                .header("Environment",
                        environment.equalsIgnoreCase("development")
                                ? "Development" : environment);
    }

    // ---------------- PRODUCT OPERATIONS ----------------

    public String insertProduct(Product product) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("prodName", product.getProdName());
        data.put("prodDescription", product.getProdDescription());
        data.put("prodPrice", product.getProdPrice());
        data.put("prodQuantity", product.getProdQuantity());
        data.put("prodImage", product.getProdImage());
        data.put("prodCategory", product.getProdCategory());
        data.put("active", product.isActive());

        return authenticatedRequest(
                restClient.post()
                        .uri(getTableUrl("Product"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(data))
        ).retrieve().body(String.class);
    }

    public String getAllProducts() {

        return authenticatedRequest(
                restClient.get()
                        .uri(getTableUrl("Product"))
        ).retrieve().body(String.class);
    }

    public String updateProduct(Product product) {

        if (product.getProductId() == null || product.getProductId().isBlank()) {
            throw new IllegalArgumentException("Product ROWID is required for updating a product");
        }

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("ROWID", Long.parseLong(product.getProductId()));
        if (product.getProdName() != null) data.put("prodName", product.getProdName());
        if (product.getProdDescription() != null) data.put("prodDescription", product.getProdDescription());
        if (product.getProdPrice() != null) data.put("prodPrice", product.getProdPrice());
        data.put("prodQuantity", product.getProdQuantity());
        if (product.getProdImage() != null) data.put("prodImage", product.getProdImage());
        if (product.getProdCategory() != null) data.put("prodCategory", product.getProdCategory());
        data.put("active", product.isActive());

        return updateRows("Product", List.of(data));
    }

    // ---------------- CART OPERATIONS ----------------

    // Retrieve all cart rows
    public String getAllCartItems() {

        return authenticatedRequest(
                restClient.get()
                        .uri(getTableUrl("Cartitem"))
        ).retrieve().body(String.class);
    }

    // Insert a new cart row
    public String insertCartItem(
            String customerId,
            CartItem item) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("customerId", customerId);
        data.put("productId", item.getProductId());
        data.put("prodName", item.getProdName());
        data.put("prodPrice", item.getProdPrice());
        data.put("quantity", item.getQuantity());
        data.put("subtotal", item.getSubtotal());

        return authenticatedRequest(
                restClient.post()
                        .uri(getTableUrl("Cartitem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(data))
        ).retrieve().body(String.class);
    }

    // Update an existing cart row using ROWID
    public String updateCartItem(CartItem item) {

        if (item.getRowId() == null) {
            throw new IllegalArgumentException(
                    "Catalyst ROWID is required for updating a cart item"
            );
        }

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("ROWID", Long.parseLong(item.getRowId()));
        data.put("productId", item.getProductId());
        data.put("prodName", item.getProdName());
        data.put("prodPrice", item.getProdPrice());
        data.put("quantity", item.getQuantity());
        data.put("subtotal", item.getSubtotal());

        return authenticatedRequest(
                restClient.put()
                        .uri(getTableUrl("Cartitem"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(List.of(data))
        ).retrieve().body(String.class);
    }

    // Delete an existing cart row using ROWID
    public String deleteCartItem(String rowId) {

        if (rowId == null || rowId.isBlank()) {
            throw new IllegalArgumentException(
                    "Catalyst ROWID is required for deleting a cart item"
            );
        }

        try {
            return authenticatedRequest(
                    restClient.delete()
                            .uri(getTableUrl("Cartitem") + "/" + rowId)
            ).retrieve().body(String.class);
        } catch (HttpClientErrorException.NotFound ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (responseBody != null && (responseBody.contains("INVALID_ID") || responseBody.contains("No such row"))) {
                logger.info("Cartitem row {} is already absent or deleted in Catalyst (404 INVALID_ID)", rowId);
                return null;
            }
            throw ex;
        }
    }
}
