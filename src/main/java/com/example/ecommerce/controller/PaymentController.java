package com.example.ecommerce.controller;

import com.example.ecommerce.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout/{orderId}")
    public ResponseEntity<Map<String, Object>> createCheckoutSession(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.createPayment(orderId));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        try {
            return ResponseEntity.ok(paymentService.handleWebhook(payload, stripeSignature));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
    @GetMapping("/success")
    public String success(){
        return "Successfully checked out";
    }

    @PostMapping("/confirm/{orderId}")
    public ResponseEntity<?> confirmPayment(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.confirmPayment(orderId));
    }

    @GetMapping("/confirm/{orderId}")
    public ResponseEntity<?> confirmPaymentGet(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.confirmPayment(orderId));
    }

    @GetMapping("/cancel")
    public String cancel(){
        return "Payment Failed ";
    }
}
