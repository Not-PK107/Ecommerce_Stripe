package com.example.ecommerce.repository;

import com.example.ecommerce.model.Payment;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import com.zc.exception.ZCException;
import com.zc.exception.ZCServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Repository
public class PaymentRepository {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRepository.class);
    private static final String PAYMENT_TABLE = "Payment";
    private static final String EVENT_TABLE = "StripeEvent";

    private final CatalystDataStoreService dataStore;
    private final Set<String> inMemoryProcessedEvents = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean eventTableMissingWarned = new AtomicBoolean(false);

    public PaymentRepository(CatalystDataStoreService dataStore) {
        this.dataStore = dataStore;
    }

    public Payment insert(Payment payment) {
        try {
            ZCRowObject inserted = dataStore.insertRow(PAYMENT_TABLE, toRow(payment));
            String rowId = CatalystDataStoreService.getRequiredRowId(inserted, PAYMENT_TABLE);
            payment.setPaymentId(rowId);
            return payment;
        } catch (Exception ex) {
            throw operationFailed("create payment", ex);
        }
    }

    public Payment update(Payment payment) {
        try {
            dataStore.updateRow(PAYMENT_TABLE, payment.getPaymentId(), toRow(payment));
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
            List<ZCRowObject> rows = dataStore.getAllRows(PAYMENT_TABLE);
            List<Payment> payments = new ArrayList<>();
            for (ZCRowObject row : rows) {
                payments.add(toPayment(row));
            }
            return payments;
        } catch (Exception ex) {
            throw operationFailed("list payments", ex);
        }
    }

    public boolean isEventProcessed(String eventId) {
        if (inMemoryProcessedEvents.contains(eventId)) {
            return true;
        }
        try {
            List<ZCRowObject> rows = dataStore.getAllRows(EVENT_TABLE);
            for (ZCRowObject row : rows) {
                if (eventId.equals(CatalystDataStoreService.getString(row, "eventId"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            if (isMissingTableException(ex)) {
                warnMissingEventTableOnce();
                return inMemoryProcessedEvents.contains(eventId);
            }
            throw operationFailed("read processed stripe event", ex);
        }
    }

    public void markEventProcessed(String eventId) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", eventId);
            row.put("processedAt", Instant.now().toString());
            dataStore.insertRow(EVENT_TABLE, row);
        } catch (Exception ex) {
            if (isMissingTableException(ex)) {
                warnMissingEventTableOnce();
                inMemoryProcessedEvents.add(eventId);
                return;
            }
            throw operationFailed("record processed stripe event", ex);
        }
    }

    private void warnMissingEventTableOnce() {
        if (eventTableMissingWarned.compareAndSet(false, true)) {
            logger.warn("Catalyst table '{}' does not exist; falling back to in-memory webhook event idempotency tracking", EVENT_TABLE);
        }
    }

    private static boolean isMissingTableException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ZCServerException serverEx) {
                if (serverEx.getStatus() == 404 || "INVALID_ID".equalsIgnoreCase(serverEx.getCode())) {
                    return true;
                }
                String msg = serverEx.getErrorMsg();
                if (msg != null && (msg.contains("No such resource") || msg.contains("INVALID_ID"))) {
                    return true;
                }
            } else if (current instanceof ZCException zcEx) {
                if (zcEx.getStatus() == 404 || "INVALID_ID".equalsIgnoreCase(zcEx.getCode())) {
                    return true;
                }
                String msg = zcEx.getErrorMsg();
                if (msg != null && (msg.contains("No such resource") || msg.contains("INVALID_ID"))) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && (message.contains("No such resource") || message.contains("INVALID_ID"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    private Payment toPayment(ZCRowObject row) {
        return Payment.builder()
                .paymentId(CatalystDataStoreService.getString(row, "ROWID"))
                .orderId(CatalystDataStoreService.getString(row, "orderId"))
                .stripeSessionId(CatalystDataStoreService.getString(row, "stripeSessionId"))
                .stripePaymentIntentId(CatalystDataStoreService.getString(row, "stripePaymentIntentId"))
                .amount(CatalystDataStoreService.getLong(row, "amount"))
                .currency(CatalystDataStoreService.getString(row, "currency"))
                .paymentStatus(CatalystDataStoreService.getString(row, "paymentStatus"))
                .createdAt(CatalystDataStoreService.getString(row, "CREATEDTIME"))
                .updatedAt(CatalystDataStoreService.getString(row, "MODIFIEDTIME"))
                .build();
    }

    private IllegalStateException operationFailed(String operation, Exception cause) {
        logger.error("Catalyst {} operation failed", operation, cause);
        return new IllegalStateException("Catalyst payment operation failed", cause);
    }
}
