package com.example.ecommerce.service;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.dto.OrderItemResponse;
import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Order;
import com.example.ecommerce.model.OrderItem;
import com.example.ecommerce.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CANCELLED = "CANCELLED";

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(
            OrderRepository orderRepository,
            CustomerService customerService,
            CartService cartService,
            ProductService productService) {
        this.orderRepository = orderRepository;
        this.customerService = customerService;
        this.cartService = cartService;
        this.productService = productService;
    }

    public OrderResponse createOrder(String customerId, OrderRequest request) {
        if (!customerId.equals(request.getCustomerId())) {
            throw new IllegalArgumentException("Request customerId must match the customerId in the URL");
        }
        customerService.getCustomerOrThrow(customerId);

        Cart cart = cartService.getCart(customerId);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot create an order from an empty cart");
        }

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, CartItem> entry : cart.getItems().entrySet()) {
            CartItem cartItem = entry.getValue();
            String productId = cartItem.getProductId() == null
                    ? entry.getKey()
                    : cartItem.getProductId();
            Product product = productService.getActiveProductOrThrow(productId);
            int quantity = cartItem.getQuantity();
            if (quantity <= 0) {
                throw new IllegalArgumentException("Cart item quantity must be greater than zero");
            }
            if (quantity > product.getProdQuantity()) {
                throw new ConflictException(
                        "Insufficient stock for product: " + product.getProductId());
            }
            if (product.getProdPrice() == null) {
                throw new IllegalStateException("Product is missing a price: " + product.getProductId());
            }


            BigDecimal subtotal = product.getProdPrice().multiply(BigDecimal.valueOf(quantity));
            total = total.add(subtotal);
            items.add(OrderItem.builder()
                    .productId(product.getProductId())
                    .prodName(product.getProdName())
                    .prodPrice(product.getProdPrice())
                    .quantity(quantity)
                    .subtotal(subtotal)
                    .build());
        }

        Order order = Order.builder()
                .customerId(customerId)
                .totalAmount(total)
                .orderStatus(PENDING_PAYMENT)
                .orderDate(Instant.now().toString())
                .items(items)
                .build();
        order.setOrderDate(
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                )
        );
        orderRepository.insertOrder(order);
        items.forEach(item -> item.setOrderId(order.getOrderId()));

        try {
            orderRepository.insertOrderItems(items);
        } catch (RuntimeException insertionFailure) {
            try {
                orderRepository.deleteOrderAndItems(order.getOrderId());
            } catch (RuntimeException compensationFailure) {
                insertionFailure.addSuppressed(compensationFailure);
                logger.error("Could not fully compensate failed order creation for order {}", order.getOrderId(),
                        compensationFailure);
            }
            throw insertionFailure;
        }

        cartService.clearCart(customerId);
        return toResponse(order);
    }

    public OrderResponse getById(String orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        return toResponse(order);
    }

    public List<OrderResponse> getByCustomerId(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse cancel(String orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        if (!PENDING_PAYMENT.equals(order.getOrderStatus())) {
            throw new ConflictException("Only unpaid pending orders can be cancelled");
        }

        order.setOrderStatus(CANCELLED);
        return toResponse(orderRepository.updateStatus(order));
    }

    public OrderResponse updateStatus(String orderId, String newStatus) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        order.setOrderStatus(newStatus);
        return toResponse(orderRepository.updateStatus(order));
    }

    public synchronized OrderResponse fulfillOrder(String orderId) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }

        if (!"PAID".equals(order.getOrderStatus()) && !"COMPLETED".equals(order.getOrderStatus())) {
            List<OrderItem> items = order.getItems();
            if (items != null) {
                for (OrderItem item : items) {
                    if (item.getProductId() != null && item.getQuantity() > 0) {
                        try {
                            productService.deductStock(item.getProductId(), item.getQuantity());
                        } catch (Exception ex) {
                            logger.error("Failed to deduct stock for product {}: {}", item.getProductId(), ex.getMessage(), ex);
                        }
                    }
                }
            }
            order.setOrderStatus("PAID");
            return toResponse(orderRepository.updateStatus(order));
        }

        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .orderStatus(order.getOrderStatus())
                .orderDate(order.getOrderDate())
                .items(items.stream()
                        .map(item -> OrderItemResponse.builder()
                                .productId(item.getProductId())
                                .prodName(item.getProdName())
                                .prodPrice(item.getProdPrice())
                                .quantity(item.getQuantity())
                                .subtotal(item.getSubtotal())
                                .build())
                        .toList())
                .build();
    }
}
