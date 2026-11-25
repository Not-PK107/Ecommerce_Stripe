
package com.example.ecommerce.repository;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.config.CatalystClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class ProductRepository {

    private static final Logger logger =
            LoggerFactory.getLogger(ProductRepository.class);

    private final ConcurrentHashMap<String, Product> localProducts =
            new ConcurrentHashMap<>();

    private final AtomicLong idGenerator = new AtomicLong(1);

    private final CatalystClient catalystClient;
    private final ObjectMapper objectMapper;
    private final boolean catalystEnabled;

    public ProductRepository(
            CatalystClient catalystClient,
            ObjectMapper objectMapper,
            @Value("${app.catalyst.enabled:false}") boolean catalystEnabled) {

        this.catalystClient = catalystClient;
        this.objectMapper = objectMapper;
        this.catalystEnabled = catalystEnabled;
        initDefaultLocalProducts();
    }

    // Save a new product
    public Product save(Product product) {

        if (product.getProductId() != null && !product.getProductId().isBlank()) {
            return update(product);
        }

        if (!catalystEnabled) {
            return saveLocally(product);
        }

        try {
            String response = catalystClient.insertProduct(product);

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            if (data.isArray() && !data.isEmpty()) {
                JsonNode insertedProduct = data.get(0);

                if (insertedProduct.has("ROWID")) {
                    product.setProductId(
                            insertedProduct.get("ROWID").asText());
                }
            }

            return product;

        } catch (Exception ex) {
            throw catalystOperationFailed("save product", ex);
        }
    }

    // Update an existing product
    public Product update(Product product) {

        if (product.getProductId() == null || product.getProductId().isBlank()) {
            throw new IllegalArgumentException("Product ID is required for update");
        }

        if (!catalystEnabled) {
            localProducts.put(product.getProductId(), product);
            return product;
        }

        try {
            catalystClient.updateProduct(product);
            return product;
        } catch (Exception ex) {
            throw catalystOperationFailed("update product " + product.getProductId(), ex);
        }
    }

    // Retrieve all active products
    public List<Product> findAll() {

        if (!catalystEnabled) {
            return localProducts.values()
                    .stream()
                    .filter(Product::isActive)
                    .toList();
        }

        try {
            String response = catalystClient.getAllProducts();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");

            List<Product> products = new ArrayList<>();

            if (!data.isArray()) {
                throw new IllegalStateException(
                        "Unexpected response from Catalyst: data is not an array");
            }

            for (JsonNode row : data) {
                Product product = mapJsonToProduct(row);

                if (product != null && product.isActive()) {
                    products.add(product);
                }
            }

            return products;

        } catch (Exception ex) {
            throw catalystOperationFailed("list products", ex);
        }
    }

    // Find a product by ID
    public Product findById(String productId) {

        if (!catalystEnabled) {
            Product product = localProducts.get(productId);

            return product != null && product.isActive()
                    ? product : null;
        }

        try {
            return findAll()
                    .stream()
                    .filter(product ->
                            productId.equals(product.getProductId()))
                    .findFirst()
                    .orElse(null);

        } catch (Exception ex) {
            throw catalystOperationFailed(
                    "find product " + productId, ex);
        }
    }

    // Soft delete
    public boolean deleteById(String productId) {

        if (!catalystEnabled) {
            Product product = localProducts.get(productId);

            if (product == null) {
                return false;
            }

            product.setActive(false);
            return true;
        }

        throw new UnsupportedOperationException(
                "Soft delete is not implemented in CatalystClient yet");
    }

    // Local storage fallback
    private Product saveLocally(Product product) {

        if (product.getProductId() == null
                || product.getProductId().isBlank()) {

            product.setProductId(
                    String.valueOf(idGenerator.getAndIncrement()));
        }

        localProducts.put(product.getProductId(), product);

        return product;
    }

    // Convert Catalyst JSON row into Product
    private Product mapJsonToProduct(JsonNode row) {

        if (row == null || row.isNull()) {
            return null;
        }

        Product product = new Product();

        product.setProductId(getString(row, "ROWID"));
        product.setProdName(getString(row, "prodName"));
        product.setProdDescription(getString(row, "prodDescription"));

        if (row.hasNonNull("prodPrice")) {
            product.setProdPrice(
                    new BigDecimal(row.get("prodPrice").asText()));
        }

        product.setProdQuantity(
                row.hasNonNull("prodQuantity")
                        ? row.get("prodQuantity").asInt()
                        : 0);

        String image = getString(row, "prodImage");
        if (image == null || image.isBlank()) {
            image = resolveDefaultImage(product.getProductId(), product.getProdName());
        }
        product.setProdImage(image);
        product.setProdCategory(getString(row, "prodCategory"));

        product.setActive(
                !row.has("active") || row.get("active").asBoolean(true));

        return product;
    }

    private String resolveDefaultImage(String id, String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if ("89157000000114003".equals(id) || lower.contains("iphone") || lower.contains("16 pro")) {
            return "https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=800&q=80";
        }
        if ("89157000000053001".equals(id) || lower.contains("s24") || lower.contains("samsung")) {
            return "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=800&q=80";
        }
        if ("89157000000083004".equals(id) || lower.contains("macbook") || (lower.contains("apple") && !lower.contains("iphone")) || lower.contains("m1")) {
            return "https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=800&q=80";
        }
        if ("89157000000083005".equals(id) || lower.contains("ram") || lower.contains("ddr4") || lower.contains("ddr")) {
            return "https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80";
        }
        return null;
    }

    private void initDefaultLocalProducts() {
        Product p1 = Product.builder()
                .productId("89157000000053001")
                .prodName("Samsung Galaxy S24 Ultra")
                .prodDescription("Titanium Gray, 256GB with Galaxy AI and Dynamic AMOLED 2X Display")
                .prodPrice(new BigDecimal("129999.00"))
                .prodQuantity(15)
                .prodImage("https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=800&q=80")
                .prodCategory("Smartphones")
                .active(true)
                .build();
        Product p2 = Product.builder()
                .productId("89157000000083004")
                .prodName("MacBook Pro M1 Pro")
                .prodDescription("14-inch Liquid Retina XDR display, 16GB Unified Memory, 512GB SSD")
                .prodPrice(new BigDecimal("164900.00"))
                .prodQuantity(8)
                .prodImage("https://images.unsplash.com/photo-1611186871348-b1ce696e52c9?auto=format&fit=crop&w=800&q=80")
                .prodCategory("Laptops")
                .active(true)
                .build();
        Product p3 = Product.builder()
                .productId("89157000000083005")
                .prodName("DDR4 16GB 3200MHz RAM")
                .prodDescription("High-performance desktop memory module with aluminum heat spreader")
                .prodPrice(new BigDecimal("3499.00"))
                .prodQuantity(30)
                .prodImage("https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80")
                .prodCategory("Components")
                .active(true)
                .build();
        Product p4 = Product.builder()
                .productId("89157000000114003")
                .prodName("Iphone 16 Pro max")
                .prodDescription("Iphone 16 Pro Max in Natural Titanium, 256GB with Super Retina XDR display and A18 Pro chip")
                .prodPrice(new BigDecimal("90000.00"))
                .prodQuantity(11)
                .prodImage("https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=800&q=80")
                .prodCategory("Electronics")
                .active(true)
                .build();
        localProducts.put(p1.getProductId(), p1);
        localProducts.put(p2.getProductId(), p2);
        localProducts.put(p3.getProductId(), p3);
        localProducts.put(p4.getProductId(), p4);
    }

    private String getString(JsonNode row, String field) {

        JsonNode value = row.get(field);

        return value == null || value.isNull()
                ? null
                : value.asText();
    }

    private IllegalStateException catalystOperationFailed(
            String operation, Exception cause) {

        logger.error("Failed to {} in Catalyst Product table", operation, cause);

        return new IllegalStateException(
                "Failed to " + operation + " in Catalyst Product table",
                cause);
    }
}
