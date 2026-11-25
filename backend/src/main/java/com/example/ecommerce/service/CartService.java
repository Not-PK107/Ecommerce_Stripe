
package com.example.ecommerce.service;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.dto.AddToCartRequest;
import com.example.ecommerce.dto.UpdateCartRequest;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.repository.CartRepository;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;

    public CartService(
            CartRepository cartRepository,
            ProductService productService) {

        this.cartRepository = cartRepository;
        this.productService = productService;
    }

    // Retrieve cart
    public Cart getCart(String customerId) {

        return cartRepository.getOrCreate(customerId);
    }

    // Add product to cart
    public Cart addItem(
            String customerId,
            AddToCartRequest request) {

        Product product =
                productService.getActiveProductOrThrow(
                        request.getProductId()
                );

        Cart cart = cartRepository.getOrCreate(customerId);

        Map<String, CartItem> items = cart.getItems();

        int requestedQuantity = request.getQuantity();

        CartItem item = items.get(product.getProductId());

        int currentQuantity = item == null
                ? 0
                : item.getQuantity();

        if (currentQuantity + requestedQuantity
                > product.getProdQuantity()) {

            throw new IllegalArgumentException(
                    "Requested quantity exceeds available stock"
            );
        }

        if (item == null) {

            item = CartItem.builder()
                    .productId(product.getProductId())
                    .prodName(product.getProdName())
                    .prodPrice(product.getProdPrice())
                    .quantity(requestedQuantity)
                    .build();

        } else {

            item.setQuantity(
                    item.getQuantity() + requestedQuantity
            );

            item.setProdName(product.getProdName());
            item.setProdPrice(product.getProdPrice());
        }

        item.recalculateSubtotal();

        items.put(product.getProductId(), item);

        return cartRepository.save(customerId, cart);
    }

    // Update existing cart item
    public Cart updateItemQuantity(
            String customerId,
            String productId,
            UpdateCartRequest request) {

        Cart cart = cartRepository.findByCustomerId(customerId);

        if (cart == null) {
            throw new ResourceNotFoundException(
                    "Cart not found for customer: " + customerId
            );
        }

        CartItem item = cart.getItems().get(productId);

        if (item == null) {
            throw new ResourceNotFoundException(
                    "Cart item not found for product: " + productId
            );
        }

        Product product =
                productService.getActiveProductOrThrow(productId);

        int updatedQuantity = request.getQuantity();

        if (updatedQuantity > product.getProdQuantity()) {
            throw new IllegalArgumentException(
                    "Requested quantity exceeds available stock"
            );
        }

        item.setProdName(product.getProdName());
        item.setProdPrice(product.getProdPrice());
        item.setQuantity(updatedQuantity);

        item.recalculateSubtotal();

        return cartRepository.save(customerId, cart);
    }

    // Remove one item from cart
    public Cart removeItem(
            String customerId,
            String productId) {

        cartRepository.deleteItem(customerId, productId);

        return cartRepository.getOrCreate(customerId);
    }

    // Clear customer's cart
    public void clearCart(String customerId) {

        cartRepository.deleteByCustomerId(customerId);
    }
}
