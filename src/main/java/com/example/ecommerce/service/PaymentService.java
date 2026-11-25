package com.example.ecommerce.service;

import com.example.ecommerce.dto.OrderItemResponse;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.model.Payment;
import com.example.ecommerce.repository.PaymentRepository;
import com.stripe.StripeClient;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<String> PENDING_STATUSES = Set.of("PENDING", "PENDING_PAYMENT");

    private final StripeClient stripeClient;
    private final OrderService orderService;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.webhook-secret:whsec_test_dummy}")
    private String stripeWebhookSecret;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public Map<String, Object> createPayment(String orderId) {
        OrderResponse orderResponse = orderService.getById(orderId);
        if (!PENDING_STATUSES.contains(orderResponse.getOrderStatus())) {
            throw new IllegalStateException("Payment can only be created for pending orders");
        }

        List<OrderItemResponse> items = orderResponse.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Order has no items to checkout");
        }

        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalAmountInPaise = 0;

        for (OrderItemResponse item : items) {
            if (item.getProdPrice() == null || item.getProdPrice().signum() <= 0) {
                throw new IllegalStateException("Order item has an invalid product price");
            }
            int quantity = item.getQuantity();
            if (quantity <= 0) {
                throw new IllegalStateException("Order item quantity must be greater than zero");
            }

            BigDecimal unitPrice = item.getProdPrice().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(subtotal);

            long amountInPaise = unitPrice.movePointRight(2).longValueExact();
            totalAmountInPaise = Math.addExact(
                    totalAmountInPaise,
                    Math.multiplyExact(amountInPaise, (long) quantity));

            SessionCreateParams.LineItem.PriceData.ProductData productData = SessionCreateParams.LineItem.PriceData.ProductData.builder()
                    .setName(item.getProdName())
                    .build();

            SessionCreateParams.LineItem.PriceData priceData = SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency("inr")
                    .setUnitAmount(amountInPaise)
                    .setProductData(productData)
                    .build();

            SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                    .setQuantity((long) quantity)
                    .setPriceData(priceData)
                    .build();

            lineItems.add(lineItem);
        }

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.UNNECESSARY);
        if (orderResponse.getTotalAmount() == null
                || normalizedTotal.compareTo(orderResponse.getTotalAmount()) != 0) {
            throw new IllegalStateException("Order total mismatch while creating checkout session");
        }

        SessionCreateParams sessionCreateParams = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/?checkout=success&orderId=" + orderResponse.getOrderId())
                .setCancelUrl(frontendUrl + "/?checkout=cancelled&orderId=" + orderResponse.getOrderId())
                .addAllLineItem(lineItems)
                .putMetadata("orderId", orderResponse.getOrderId())
                .putMetadata("customerId", orderResponse.getCustomerId())
                .build();

        Session session;
        try {
            session = stripeClient.v1().checkout().sessions().create(sessionCreateParams);
        } catch (StripeException ex) {
            throw new IllegalStateException("Failed to create Stripe checkout session", ex);
        }

        Payment payment = Payment.builder()
                .orderId(orderId)
                .stripeSessionId(session.getId())
                .stripePaymentIntentId(session.getPaymentIntent())
                .amount(totalAmountInPaise)
                .currency("INR")
                .paymentStatus("PENDING")
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();

        Payment savedPayment = paymentRepository.insert(payment);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", orderId);
        response.put("paymentId", savedPayment.getPaymentId());
        response.put("sessionId", savedPayment.getStripeSessionId());
        response.put("checkoutUrl", session.getUrl());
        response.put("currency", "INR");
        response.put("amount", normalizedTotal);
        return response;
    }

    public OrderResponse confirmPayment(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment != null && !"PAID".equals(payment.getPaymentStatus())) {
            payment.setPaymentStatus("PAID");
            payment.setUpdatedAt(Instant.now().toString());
            paymentRepository.update(payment);
        }
        return orderService.fulfillOrder(orderId);
    }

    public String handleWebhook(String payload, String stripeSignature) {
        if (stripeSignature == null || stripeSignature.isBlank()) {
            throw new IllegalArgumentException("Missing Stripe signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, stripeSignature, stripeWebhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new IllegalArgumentException("Invalid Stripe signature", ex);
        }

        String eventId = event.getId();
        if (paymentRepository.isEventProcessed(eventId)) {
            return "duplicate";
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session;
            try {
                session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                throw new IllegalStateException("Failed to deserialize Stripe checkout session payload", ex);
            }
            if (session == null) {
                throw new IllegalStateException("Stripe session payload was missing");
            }

            String orderId = session.getMetadata() == null ? null : session.getMetadata().get("orderId");
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("Stripe checkout session was missing the orderId metadata");
            }

            if (!"paid".equalsIgnoreCase(session.getPaymentStatus())) {
                return "ignored";
            }

            Payment payment = paymentRepository.findByStripeSessionId(session.getId())
                    .orElseThrow(() -> new IllegalStateException("Payment not found for Stripe session: " + session.getId()));

            if (!orderId.equals(payment.getOrderId())) {
                throw new IllegalStateException("Stripe session order does not match stored payment");
            }

            if (payment.getAmount() == null
                    || session.getAmountTotal() == null
                    || !payment.getAmount().equals(session.getAmountTotal())) {
                throw new IllegalStateException("Stripe session total does not match the stored payment amount");
            }
            if (!"inr".equalsIgnoreCase(session.getCurrency())
                    || !"INR".equalsIgnoreCase(payment.getCurrency())) {
                throw new IllegalStateException("Stripe session currency does not match the stored payment currency");
            }

            payment.setStripePaymentIntentId(session.getPaymentIntent());
            payment.setCurrency(session.getCurrency() == null ? payment.getCurrency() : session.getCurrency().toUpperCase());
            payment.setPaymentStatus("PAID");
            payment.setUpdatedAt(Instant.now().toString());
            paymentRepository.update(payment);
            orderService.fulfillOrder(orderId);
            paymentRepository.markEventProcessed(eventId);
            return "ok";
        }

        if ("checkout.session.async_payment_succeeded".equals(event.getType())
                || "checkout.session.async_payment_failed".equals(event.getType())) {
            Session session;
            try {
                session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                throw new IllegalStateException("Failed to deserialize Stripe async checkout session payload", ex);
            }
            if (session == null) {
                throw new IllegalStateException("Stripe async session payload was missing");
            }

            String orderId = session.getMetadata() == null ? null : session.getMetadata().get("orderId");
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("Stripe async checkout session was missing the orderId metadata");
            }

            Payment payment = paymentRepository.findByStripeSessionId(session.getId())
                    .orElse(null);
            if (payment != null) {
                payment.setStripePaymentIntentId(session.getPaymentIntent());
                payment.setUpdatedAt(Instant.now().toString());
                if ("checkout.session.async_payment_succeeded".equals(event.getType())) {
                    payment.setPaymentStatus("PAID");
                    paymentRepository.update(payment);
                    orderService.fulfillOrder(orderId);
                } else {
                    payment.setPaymentStatus("FAILED");
                    paymentRepository.update(payment);
                }
            }
            paymentRepository.markEventProcessed(eventId);
            return "ok";
        }

        paymentRepository.markEventProcessed(eventId);
        return "ignored";
    }

}
