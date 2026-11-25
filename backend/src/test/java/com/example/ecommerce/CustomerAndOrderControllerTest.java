package com.example.ecommerce;

import com.example.ecommerce.controller.CustomerController;
import com.example.ecommerce.controller.OrderController;
import com.example.ecommerce.dto.CustomerRequest;
import com.example.ecommerce.dto.CustomerResponse;
import com.example.ecommerce.dto.OrderRequest;
import com.example.ecommerce.dto.OrderResponse;
import com.example.ecommerce.exception.GlobalExceptionHandler;
import com.example.ecommerce.service.CustomerService;
import com.example.ecommerce.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerAndOrderControllerTest {

    private MockMvc mockMvc;
    private CustomerService customerService;
    private OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        customerService = mock(CustomerService.class);
        orderService = mock(OrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CustomerController(customerService),
                        new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void customerEndpoints_ReturnExpectedStatuses() throws Exception {
        CustomerRequest request = new CustomerRequest();
        request.setCustomerName("Customer One");
        request.setEmail("customer@example.com");
        request.setPhone("1234567890");
        request.setAddress("1 Example Street");
        CustomerResponse response = CustomerResponse.builder()
                .customerId("11")
                .customerName("Customer One")
                .email("customer@example.com")
                .phone("1234567890")
                .address("1 Example Street")
                .createdAt("2026-01-01T00:00:00Z")
                .build();
        when(customerService.register(any(CustomerRequest.class))).thenReturn(response);
        when(customerService.login("customer@example.com")).thenReturn(response);
        when(customerService.getById("11")).thenReturn(response);
        when(customerService.getAll()).thenReturn(List.of(response));
        when(customerService.update(eq("11"), any(CustomerRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("11"));
        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("11"));
        mockMvc.perform(get("/api/customers/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@example.com"));
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value("11"));
        mockMvc.perform(put("/api/customers/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/customers/11"))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerAndLogin_WhenEmailIsInvalid_ReturnsBadRequest() throws Exception {
        CustomerRequest request = new CustomerRequest();
        request.setCustomerName("Invalid User");
        request.setEmail("invalid-email-format");
        request.setPhone("1234567890");
        request.setAddress("1 Example Street");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.email").exists());

        mockMvc.perform(post("/api/customers/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void register_WhenNameContainsNumbers_ReturnsBadRequest() throws Exception {
        CustomerRequest request = new CustomerRequest();
        request.setCustomerName("John123");
        request.setEmail("john@example.com");
        request.setPhone("1234567890");
        request.setAddress("1 Example Street");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.customerName").exists());
    }

    @Test
    void register_WhenPhoneIsInvalid_ReturnsBadRequest() throws Exception {
        CustomerRequest request = new CustomerRequest();
        request.setCustomerName("John Doe");
        request.setEmail("john@example.com");
        request.setPhone("12345abcde");
        request.setAddress("1 Example Street");

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.phone").exists());

        request.setPhone("123456789012");
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.phone").exists());
    }

    @Test
    void orderEndpoints_ReturnExpectedStatuses() throws Exception {
        OrderRequest request = new OrderRequest();
        request.setCustomerId("11");
        OrderResponse response = OrderResponse.builder()
                .orderId("22")
                .customerId("11")
                .totalAmount(new BigDecimal("9.00"))
                .orderStatus("PENDING_PAYMENT")
                .orderDate("2026-01-01T00:00:00Z")
                .items(List.of())
                .build();
        when(orderService.createOrder(eq("11"), any(OrderRequest.class))).thenReturn(response);
        when(orderService.getById("22")).thenReturn(response);
        when(orderService.getByCustomerId("11")).thenReturn(List.of(response));
        when(orderService.cancel("22")).thenReturn(response);

        mockMvc.perform(post("/api/orders/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("22"));
        mockMvc.perform(get("/api/orders/22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"));
        mockMvc.perform(get("/api/orders/customer/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("22"));
        mockMvc.perform(put("/api/orders/22/cancel"))
                .andExpect(status().isOk());
    }
}
