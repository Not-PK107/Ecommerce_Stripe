package com.example.ecommerce;

import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.service.CartService;
import com.example.ecommerce.service.CustomerService;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @Test
    void createOrder_UsesCurrentProductPriceAndClearsCartAfterPersistingItems() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerService customerService = mock(CustomerService.class);
        CartService cartService = mock(CartService.class);
        ProductService productService = mock(ProductService.class);
        OrderService service = new OrderService(orderRepository, customerService, cartService, productService);

        CartItem cartItem = CartItem.builder()
                .productId("product-1")
                .prodName("Stale name")
                .prodPrice(new BigDecimal("0.01"))
                .quantity(2)
                .subtotal(new BigDecimal("0.02"))
                .build();
        Cart cart = new Cart("customer-1", new LinkedHashMap<>());
        cart.getItems().put("product-1", cartItem);
        when(cartService.getCart("customer-1")).thenReturn(cart);
        when(productService.getActiveProductOrThrow("product-1")).thenReturn(
                Product.builder()
                        .productId("product-1")
                        .prodName("Current name")
                        .prodPrice(new BigDecimal("4.50"))
                        .prodQuantity(10)
                        .build());
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId("51");
            return order;
        }).when(orderRepository).insertOrder(any(Order.class));

        OrderResponse response = service.createOrder("customer-1", request("customer-1"));

        assertEquals("51", response.getOrderId());
        assertEquals(new BigDecimal("9.00"), response.getTotalAmount());
        assertEquals("PENDING_PAYMENT", response.getOrderStatus());
        assertEquals("Current name", response.getItems().get(0).getProdName());
        assertEquals(new BigDecimal("4.50"), response.getItems().get(0).getProdPrice());
        verify(orderRepository).insertOrderItems(argThat(items ->
                items.size() == 1
                        && "51".equals(items.get(0).getOrderId())
                        && new BigDecimal("9.00").equals(items.get(0).getSubtotal())));
        verify(cartService).clearCart("customer-1");
    }

    @Test
    void createOrder_WhenItemInsertFails_CompensatesAndKeepsCart() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerService customerService = mock(CustomerService.class);
        CartService cartService = mock(CartService.class);
        ProductService productService = mock(ProductService.class);
        OrderService service = new OrderService(orderRepository, customerService, cartService, productService);

        Cart cart = new Cart("customer-1", new LinkedHashMap<>());
        cart.getItems().put("product-1", CartItem.builder()
                .productId("product-1").quantity(1).build());
        when(cartService.getCart("customer-1")).thenReturn(cart);
        when(productService.getActiveProductOrThrow("product-1")).thenReturn(
                Product.builder().productId("product-1").prodName("Item")
                        .prodPrice(BigDecimal.ONE).prodQuantity(5).build());
        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setOrderId("51");
            return order;
        }).when(orderRepository).insertOrder(any(Order.class));
        doThrow(new IllegalStateException("Item insert failed"))
                .when(orderRepository).insertOrderItems(any());

        assertThrows(IllegalStateException.class,
                () -> service.createOrder("customer-1", request("customer-1")));

        verify(orderRepository).deleteOrderAndItems("51");
        verify(cartService, never()).clearCart("customer-1");
    }

    @Test
    void createOrder_WhenStockIsInsufficient_DoesNotCreateOrder() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerService customerService = mock(CustomerService.class);
        CartService cartService = mock(CartService.class);
        ProductService productService = mock(ProductService.class);
        OrderService service = new OrderService(orderRepository, customerService, cartService, productService);

        Cart cart = new Cart("customer-1", new LinkedHashMap<>());
        cart.getItems().put("product-1", CartItem.builder()
                .productId("product-1").quantity(2).build());
        when(cartService.getCart("customer-1")).thenReturn(cart);
        when(productService.getActiveProductOrThrow("product-1")).thenReturn(
                Product.builder().productId("product-1").prodName("Item")
                        .prodPrice(BigDecimal.ONE).prodQuantity(1).build());

        assertThrows(ConflictException.class,
                () -> service.createOrder("customer-1", request("customer-1")));

        verify(orderRepository, never()).insertOrder(any());
    }

    @Test
    void fulfillOrder_DeductsStockAndUpdatesStatusToPaid() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        CustomerService customerService = mock(CustomerService.class);
        CartService cartService = mock(CartService.class);
        ProductService productService = mock(ProductService.class);
        OrderService service = new OrderService(orderRepository, customerService, cartService, productService);

        com.example.ecommerce.model.OrderItem item = com.example.ecommerce.model.OrderItem.builder()
                .productId("product-1")
                .prodName("iPhone 16 Pro Max")
                .quantity(2)
                .prodPrice(new BigDecimal("90000.00"))
                .subtotal(new BigDecimal("180000.00"))
                .build();

        Order order = Order.builder()
                .orderId("51")
                .customerId("customer-1")
                .orderStatus("PENDING_PAYMENT")
                .items(List.of(item))
                .build();

        when(orderRepository.findById("51")).thenReturn(order);
        when(orderRepository.updateStatus(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.fulfillOrder("51");

        assertEquals("PAID", response.getOrderStatus());
        verify(productService).deductStock("product-1", 2);
        verify(orderRepository).updateStatus(order);
    }

    private OrderRequest request(String customerId) {
        OrderRequest request = new OrderRequest();
        request.setCustomerId(customerId);
        return request;
    }
}
