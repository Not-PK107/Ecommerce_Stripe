package com.example.ecommerce;

import com.example.ecommerce.controller.PaymentController;
import com.example.ecommerce.dto.OrderItemResponse;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.exception.GlobalExceptionHandler;
import com.example.ecommerce.model.Payment;
import com.example.ecommerce.repository.PaymentRepository;
import com.example.ecommerce.service.OrderService;
import com.example.ecommerce.service.PaymentService;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentServiceAndControllerTest {

    private StripeClient stripeClient;
    private OrderService orderService;
    private PaymentRepository paymentRepository;
    private PaymentService paymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        stripeClient = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        orderService = mock(OrderService.class);
        paymentRepository = mock(PaymentRepository.class);
        paymentService = new PaymentService(stripeClient, orderService, paymentRepository);
        ReflectionTestUtils.setField(paymentService, "stripeWebhookSecret", "whsec_test_secret");
        ReflectionTestUtils.setField(paymentService, "frontendUrl", "http://localhost:2000");

        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createPayment_CreatesStripeSessionAndStoresPayment() throws Exception {
        OrderResponse order = OrderResponse.builder()
                .orderId("5001")
                .customerId("cust-1")
                .totalAmount(new BigDecimal("199.00"))
                .orderStatus("PENDING_PAYMENT")
                .items(List.of(OrderItemResponse.builder()
                        .productId("prod-1")
                        .prodName("Mouse")
                        .prodPrice(new BigDecimal("199.00"))
                        .quantity(1)
                        .subtotal(new BigDecimal("199.00"))
                        .build()))
                .build();
        when(orderService.getById("5001")).thenReturn(order);

        Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session_1");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_1");
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_session_1");
        when(stripeClient.v1().checkout().sessions().create(any(SessionCreateParams.class)))
                .thenReturn(mockSession);

        when(paymentRepository.insert(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setPaymentId("7001");
            return p;
        });

        mockMvc.perform(post("/api/payments/checkout/5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("5001"))
                .andExpect(jsonPath("$.paymentId").value("7001"))
                .andExpect(jsonPath("$.sessionId").value("cs_test_session_1"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/pay/cs_test_session_1"))
                .andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void confirmPayment_UpdatesPaymentStatusAndFulfillsOrder() throws Exception {
        Payment pending = Payment.builder()
                .paymentId("7001")
                .orderId("5001")
                .stripeSessionId("cs_test_session_1")
                .amount(19900L)
                .currency("INR")
                .paymentStatus("PENDING")
                .build();
        when(orderService.getById("5001")).thenReturn(OrderResponse.builder()
                .orderId("5001")
                .orderStatus("PENDING_PAYMENT")
                .build());
        when(paymentRepository.findByOrderId("5001")).thenReturn(Optional.of(pending));

        Session paidSession = mock(Session.class);
        when(paidSession.getPaymentStatus()).thenReturn("paid");
        when(paidSession.getPaymentIntent()).thenReturn("pi_test_99");
        when(paidSession.getAmountTotal()).thenReturn(19900L);
        when(paidSession.getCurrency()).thenReturn("inr");
        when(paidSession.getMetadata()).thenReturn(Map.of("orderId", "5001"));
        when(stripeClient.v1().checkout().sessions().retrieve("cs_test_session_1"))
                .thenReturn(paidSession);

        when(orderService.fulfillOrder("5001")).thenReturn(OrderResponse.builder()
                .orderId("5001")
                .orderStatus("PAID")
                .build());

        mockMvc.perform(post("/api/payments/confirm/5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("5001"))
                .andExpect(jsonPath("$.orderStatus").value("PAID"));

        verify(paymentRepository).update(argThat(p ->
                "PAID".equals(p.getPaymentStatus()) && "pi_test_99".equals(p.getStripePaymentIntentId())));
        verify(orderService).fulfillOrder("5001");
    }

    @Test
    void confirmPayment_WhenStripeSessionUnpaid_ReturnsConflictAndDoesNotFulfillOrder() throws Exception {
        Payment pending = Payment.builder()
                .paymentId("7001")
                .orderId("5001")
                .stripeSessionId("cs_test_session_unpaid")
                .amount(19900L)
                .currency("INR")
                .paymentStatus("PENDING")
                .build();
        when(orderService.getById("5001")).thenReturn(OrderResponse.builder()
                .orderId("5001")
                .orderStatus("PENDING_PAYMENT")
                .build());
        when(paymentRepository.findByOrderId("5001")).thenReturn(Optional.of(pending));

        Session unpaidSession = mock(Session.class);
        when(unpaidSession.getPaymentStatus()).thenReturn("unpaid");
        when(unpaidSession.getMetadata()).thenReturn(Map.of("orderId", "5001"));
        when(stripeClient.v1().checkout().sessions().retrieve("cs_test_session_unpaid"))
                .thenReturn(unpaidSession);

        mockMvc.perform(post("/api/payments/confirm/5001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Payment has not been completed in Stripe"));

        verify(paymentRepository, never()).update(any(Payment.class));
        verify(orderService, never()).fulfillOrder("5001");
    }

    @Test
    void webhook_WhenMissingOrInvalidSignature_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Missing Stripe signature"));

        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", "t=123,v1=invalidsig")
                        .content("{\"id\":\"evt_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid Stripe signature"));
    }

    @Test
    void handleWebhook_WhenSecretBlank_ThrowsIllegalStateException() {
        ReflectionTestUtils.setField(paymentService, "stripeWebhookSecret", "");
        assertThrows(IllegalStateException.class,
                () -> paymentService.handleWebhook("{}", "t=1,v1=sig"));
    }

    @Test
    void successAndCancelEndpoints_PreserveExistingResponses() throws Exception {
        mockMvc.perform(get("/api/payments/success"))
                .andExpect(status().isOk())
                .andExpect(content().string("Successfully checked out"));

        mockMvc.perform(get("/api/payments/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment Failed "));
    }

    @Test
    void webhook_WithValidSignedCheckoutCompletedEvent_FulfillsOrderAndMarksIdempotent() throws Exception {
        String payload = """
                {
                  "id": "evt_signed_1",
                  "object": "event",
                  "api_version": "%s",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_session_1",
                      "object": "checkout.session",
                      "amount_total": 19900,
                      "currency": "inr",
                      "payment_intent": "pi_test_99",
                      "payment_status": "paid",
                      "metadata": {
                        "orderId": "5001"
                      }
                    }
                  }
                }
                """.formatted(Stripe.API_VERSION);

        String signature = signStripePayload(payload, "whsec_test_secret");
        Payment pending = Payment.builder()
                .paymentId("7001")
                .orderId("5001")
                .stripeSessionId("cs_test_session_1")
                .amount(19900L)
                .currency("INR")
                .paymentStatus("PENDING")
                .build();

        when(paymentRepository.isEventProcessed("evt_signed_1")).thenReturn(false).thenReturn(true);
        when(paymentRepository.findByStripeSessionId("cs_test_session_1")).thenReturn(Optional.of(pending));

        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(paymentRepository).update(argThat(p ->
                "PAID".equals(p.getPaymentStatus()) && "pi_test_99".equals(p.getStripePaymentIntentId())));
        verify(orderService).fulfillOrder("5001");
        verify(paymentRepository).markEventProcessed("evt_signed_1");

        mockMvc.perform(post("/api/payments/webhook")
                        .header("Stripe-Signature", signature)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("duplicate"));
    }

    private static String signStripePayload(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
    }
}
