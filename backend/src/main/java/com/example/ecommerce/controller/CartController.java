package com.example.ecommerce.controller;

import com.example.ecommerce.dto.AddToCartRequest;
import com.example.ecommerce.dto.UpdateCartRequest;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/{customerId}/items")
    public ResponseEntity<Cart> addItem(
            @PathVariable String customerId,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addItem(customerId, request));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<Cart> getCart(@PathVariable String customerId) {
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PutMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> updateItemQuantity(
            @PathVariable String customerId,
            @PathVariable String productId,
            @Valid @RequestBody UpdateCartRequest request) {
        return ResponseEntity.ok(cartService.updateItemQuantity(customerId, productId, request));
    }

    @DeleteMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable String customerId,
            @PathVariable String productId) {
        cartService.removeItem(customerId, productId);
        return ResponseEntity.noContent().build();
    }
}
