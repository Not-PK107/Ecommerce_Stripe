package com.example.ecommerce;

import com.example.ecommerce.model.Payment;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import com.zc.exception.ZCServerException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRepositoryTest {

    private final CatalystDataStoreService dataStore = mock(CatalystDataStoreService.class);
    private final PaymentRepository repository = new PaymentRepository(dataStore);

    @Test
    void insertAndUpdate_SetsPaymentIdAndUpdatesFields() {
        ZCRowObject inserted = ZCRowObject.getInstance();
        inserted.set("ROWID", 7001L);
        when(dataStore.insertRow(eq("Payment"), anyMap())).thenReturn(inserted);

        Payment payment = Payment.builder()
                .orderId("5001")
                .stripeSessionId("cs_test_123")
                .amount(19900L)
                .currency("INR")
                .paymentStatus("PENDING")
                .build();

        Payment saved = repository.insert(payment);
        assertEquals("7001", saved.getPaymentId());

        saved.setPaymentStatus("PAID");
        saved.setStripePaymentIntentId("pi_test_456");
        repository.update(saved);

        verify(dataStore).updateRow(eq("Payment"), eq("7001"), argThat(fields ->
                "PAID".equals(fields.get("paymentStatus"))
                        && "pi_test_456".equals(fields.get("stripePaymentIntentId"))));
    }

    @Test
    void findByOrderIdAndSessionId_ReturnsMatchingPayment() {
        ZCRowObject row = ZCRowObject.getInstance();
        row.set("ROWID", 7001L);
        row.set("orderId", "5001");
        row.set("stripeSessionId", "cs_test_123");
        row.set("stripePaymentIntentId", "pi_test_456");
        row.set("amount", 19900L);
        row.set("currency", "INR");
        row.set("paymentStatus", "PAID");
        row.set("CREATEDTIME", "2026-11-25 10:00:00");
        row.set("MODIFIEDTIME", "2026-11-25 10:01:00");

        when(dataStore.getAllRows("Payment")).thenReturn(List.of(row));

        Optional<Payment> byOrder = repository.findByOrderId("5001");
        assertTrue(byOrder.isPresent());
        assertEquals("7001", byOrder.get().getPaymentId());
        assertEquals(19900L, byOrder.get().getAmount());
        assertEquals("2026-11-25 10:00:00", byOrder.get().getCreatedAt());

        Optional<Payment> bySession = repository.findByStripeSessionId("cs_test_123");
        assertTrue(bySession.isPresent());
        assertEquals("5001", bySession.get().getOrderId());
        assertTrue(repository.findByOrderId("9999").isEmpty());
    }

    @Test
    void stripeEventIdempotency_ChecksAndRecordsProcessedEvents() {
        ZCRowObject eventRow = ZCRowObject.getInstance();
        eventRow.set("ROWID", 8001L);
        eventRow.set("eventId", "evt_123");
        when(dataStore.getAllRows("StripeEvent")).thenReturn(List.of(eventRow));

        assertTrue(repository.isEventProcessed("evt_123"));
        assertFalse(repository.isEventProcessed("evt_999"));

        repository.markEventProcessed("evt_999");
        verify(dataStore).insertRow(eq("StripeEvent"), argThat(fields ->
                "evt_999".equals(fields.get("eventId")) && fields.get("processedAt") != null));
    }

    @Test
    void stripeEventIdempotency_WhenTableMissing_FallsBackToInMemoryTracking() {
        when(dataStore.getAllRows("StripeEvent"))
                .thenThrow(new IllegalStateException(
                        "Failed to fetch rows from Catalyst table 'StripeEvent'",
                        new ZCServerException("No such resource with the given id exists", "INVALID_ID", 404)));
        when(dataStore.insertRow(eq("StripeEvent"), anyMap()))
                .thenThrow(new IllegalStateException(
                        "Failed to insert row into Catalyst table 'StripeEvent'",
                        new ZCServerException("No such resource with the given id exists", "INVALID_ID", 404)));

        assertFalse(repository.isEventProcessed("evt_missing_table"));
        repository.markEventProcessed("evt_missing_table");
        assertTrue(repository.isEventProcessed("evt_missing_table"));
    }
}
