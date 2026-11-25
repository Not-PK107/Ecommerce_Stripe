package com.example.ecommerce.repository;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.model.Payment;
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
import java.util.Optional;

@Repository
public class PaymentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRepository.class);
    private static final String PAYMENT_TABLE = "Payment";
    private static final String EVENT_TABLE = "StripeEvent";

    private final CatalystClient catalystClient;
    private final ObjectMapper objectMapper;

    public PaymentRepository(CatalystClient catalystClient, ObjectMapper objectMapper) {
        this.catalystClient = catalystClient;
        this.objectMapper = objectMapper;
    }

    public Payment insert(Payment payment) {
        try {
            String response = catalystClient.insertRows(PAYMENT_TABLE, List.of(toRow(payment)));
            JsonNode rows = responseRows(response, PAYMENT_TABLE);
            if (rows.isEmpty() || !rows.get(0).hasNonNull("ROWID")) {
                throw new IllegalStateException("Catalyst payment insert response did not contain ROWID");
            }
            payment.setPaymentId(rows.get(0).get("ROWID").asText());
            return payment;
        } catch (Exception ex) {
            throw operationFailed("create payment", ex);
        }
    }

    public Payment update(Payment payment) {
        try {
            Map<String, Object> row = toRow(payment);
            row.put("ROWID", Long.parseLong(payment.getPaymentId()));
            catalystClient.updateRows(PAYMENT_TABLE, List.of(row));
            return payment;
        } catch (Exception ex) {
            throw operationFailed("update payment", ex);
        }
    }

    public Optional<Payment> findByOrderId(String orderId) {
        return findAll().stream()
                .filter(payment -> orderId.equals(payment.getOrderId()))
                .findFirst();
    }

    public Optional<Payment> findByStripeSessionId(String stripeSessionId) {
        return findAll().stream()
                .filter(payment -> stripeSessionId.equals(payment.getStripeSessionId()))
                .findFirst();
    }

    public List<Payment> findAll() {
        try {
            JsonNode rows = responseRows(catalystClient.getAllRows(PAYMENT_TABLE), PAYMENT_TABLE);
            List<Payment> payments = new ArrayList<>();
            for (JsonNode row : rows) {
                payments.add(toPayment(row));
            }
            return payments;
        } catch (Exception ex) {
            throw operationFailed("list payments", ex);
        }
    }

    public boolean isEventProcessed(String eventId) {
        try {
            JsonNode rows = responseRows(catalystClient.getAllRows(EVENT_TABLE), EVENT_TABLE);
            for (JsonNode row : rows) {
                if (eventId.equals(text(row, "eventId"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw operationFailed("read processed stripe event", ex);
        }
    }

    public void markEventProcessed(String eventId) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", eventId);
            row.put("processedAt", java.time.Instant.now().toString());
            catalystClient.insertRows(EVENT_TABLE, List.of(row));
        } catch (Exception ex) {
            throw operationFailed("record processed stripe event", ex);
        }
    }

    private Map<String, Object> toRow(Payment payment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", payment.getOrderId());
        row.put("stripeSessionId", payment.getStripeSessionId());
        row.put("stripePaymentIntentId", payment.getStripePaymentIntentId());
        row.put("amount", payment.getAmount());
        row.put("currency", payment.getCurrency());
        row.put("paymentStatus", payment.getPaymentStatus());
        return row;
    }

    private Payment toPayment(JsonNode row) {
        return Payment.builder()
                .paymentId(text(row, "ROWID"))
                .orderId(text(row, "orderId"))
                .stripeSessionId(text(row, "stripeSessionId"))
                .stripePaymentIntentId(text(row, "stripePaymentIntentId"))
                .amount(row.get("amount").asLong())
                .currency(text(row, "currency"))
                .paymentStatus(text(row, "paymentStatus"))
                .createdAt(text(row, "CREATEDTIME"))
                .updatedAt(text(row, "MODIFIEDTIME"))
                .build();
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
        return new IllegalStateException("Catalyst payment operation failed", cause);
    }
}
