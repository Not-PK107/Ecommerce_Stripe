package com.example.ecommerce;

import com.example.ecommerce.controller.AuthController;
import com.example.ecommerce.dto.CustomerResponse;
import com.example.ecommerce.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthController authController;
    private CustomerService customerService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        customerService = mock(CustomerService.class);
        authController = new AuthController(customerService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void getCurrentUser_WhenUnauthenticated_ReturnsAuthenticatedFalse() {
        var response = authController.getCurrentUser(null);
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("authenticated"));

        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        var anonResponse = authController.getCurrentUser(anon);
        assertNotNull(anonResponse.getBody());
        assertEquals(false, anonResponse.getBody().get("authenticated"));
    }

    @Test
    void getCurrentUser_WhenOidcUserAuthenticated_ReturnsUserProfileAndCustomer() {
        Map<String, Object> claims = Map.of(
                "sub", "zoho-12345",
                "email", "user@zoho.in",
                "name", "Test User"
        );
        OidcIdToken idToken = new OidcIdToken("tokenValue", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser oidcUser = new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                oidcUser, AuthorityUtils.createAuthorityList("ROLE_USER"), "zoho");

        CustomerResponse mockCustomer = CustomerResponse.builder()
                .customerId("cust-123")
                .customerName("Test User")
                .email("user@zoho.in")
                .phone("9999999999")
                .address("Please provide delivery address")
                .build();
        when(customerService.findOrCreateForZohoUser("user@zoho.in", "Test User")).thenReturn(mockCustomer);

        var response = authController.getCurrentUser(auth);
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("authenticated"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        assertEquals("user@zoho.in", user.get("email"));
        assertEquals("Test User", user.get("name"));
        assertEquals("zoho-12345", user.get("sub"));
        assertEquals("zoho", user.get("provider"));

        CustomerResponse customer = (CustomerResponse) response.getBody().get("customer");
        assertNotNull(customer);
        assertEquals("cust-123", customer.getCustomerId());
        assertEquals("user@zoho.in", customer.getEmail());
    }

    @Test
    void getCurrentUser_WhenOAuth2UserAuthenticated_ReturnsUserProfile() {
        Map<String, Object> attributes = Map.of(
                "email", "developer@zoho.in",
                "displayName", "Developer Zoho"
        );
        OAuth2User oauthUser = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"), attributes, "email");

        OAuth2AuthenticationToken auth = new OAuth2AuthenticationToken(
                oauthUser, AuthorityUtils.createAuthorityList("ROLE_USER"), "zoho");

        CustomerResponse mockCustomer = CustomerResponse.builder()
                .customerId("cust-456")
                .customerName("Developer Zoho")
                .email("developer@zoho.in")
                .phone("9999999999")
                .address("Please provide delivery address")
                .build();
        when(customerService.findOrCreateForZohoUser("developer@zoho.in", "Developer Zoho")).thenReturn(mockCustomer);

        var response = authController.getCurrentUser(auth);
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("authenticated"));

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        assertEquals("developer@zoho.in", user.get("email"));
        assertEquals("Developer Zoho", user.get("name"));
    }

    @Test
    void logout_InvalidatesSessionAndClearsCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var logoutResponse = authController.logout(request, response);
        assertNotNull(logoutResponse.getBody());
        assertEquals(true, logoutResponse.getBody().get("success"));
        assertTrue(session.isInvalid());
        assertNotNull(response.getCookie("JSESSIONID"));
        assertEquals(0, response.getCookie("JSESSIONID").getMaxAge());
    }

    @Test
    void redirectToZoho_RedirectsToOAuthAuthorizationEndpoint() throws Exception {
        mockMvc.perform(get("/auth/zoho/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/oauth2/authorization/zoho"));
    }
}
