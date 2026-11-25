package com.example.ecommerce;

import com.example.ecommerce.controller.CartController;
import com.example.ecommerce.controller.ProductController;
import com.example.ecommerce.dto.AddToCartRequest;
import com.example.ecommerce.exception.GlobalExceptionHandler;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.CartItem;
import com.example.ecommerce.model.Product;
import com.example.ecommerce.service.CartService;
import com.example.ecommerce.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductAndCartControllerTest {

    private MockMvc mockMvc;
    private ProductService productService;
    private CartService cartService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        cartService = mock(CartService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ProductController(productService),
                        new CartController(cartService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void addProduct_WhenRequestIsInvalid_ReturnsBadRequest() throws Exception {
        Product product = new Product();
        product.setProdName(" ");
        product.setProdDescription(" ");
        product.setProdPrice(BigDecimal.ZERO);
        product.setProdQuantity(-1);
        product.setProdImage(" ");
        product.setProdCategory(" ");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void addCartItem_WhenProductExists_ReturnsCreatedCart() throws Exception {
        AddToCartRequest request = new AddToCartRequest("p-101", 2);
        Cart cart = new Cart();
        cart.setCustomerId("customer-1");
        cart.setItems(new LinkedHashMap<>());

        CartItem item = CartItem.builder()
                .productId("p-101")
                .prodName("Laptop")
                .prodPrice(new BigDecimal("999.99"))
                .quantity(2)
                .subtotal(new BigDecimal("1999.98"))
                .build();
        cart.getItems().put("p-101", item);

        when(cartService.addItem(eq("customer-1"), any(AddToCartRequest.class))).thenReturn(cart);

        mockMvc.perform(post("/api/cart/customer-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.items.p-101.quantity").value(2));
    }

    @Test
    void getProductById_WhenProductDoesNotExist_ReturnsNotFound() throws Exception {
        when(productService.getActiveProductOrThrow("missing"))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/api/products/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void updateProduct_WhenValid_ReturnsUpdatedProduct() throws Exception {
        Product product = Product.builder()
                .productId("p-1")
                .prodName("Ceramic Mug")
                .prodDescription("Handmade mug")
                .prodPrice(new BigDecimal("18.50"))
                .prodQuantity(10)
                .prodImage("https://images.unsplash.com/photo-1.jpg")
                .prodCategory("Ceramics")
                .build();

        when(productService.updateProduct(eq("p-1"), any(Product.class))).thenReturn(product);

        mockMvc.perform(put("/api/products/p-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prodImage").value("https://images.unsplash.com/photo-1.jpg"));
    }
}
