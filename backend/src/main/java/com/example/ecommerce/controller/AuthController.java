package com.example.ecommerce.controller;

import com.example.ecommerce.dto.CustomerResponse;
import com.example.ecommerce.service.CustomerService;
import com.example.ecommerce.service.ZohoTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final CustomerService customerService;
    private final ZohoTokenService zohoTokenService;

    @Autowired
    public AuthController(CustomerService customerService, @Autowired(required = false) ZohoTokenService zohoTokenService) {
        this.customerService = customerService;
        this.zohoTokenService = zohoTokenService;
    }

    public AuthController(CustomerService customerService) {
        this(customerService, null);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }

        String email = null;
        String name = null;
        String sub = null;

        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            email = oidcUser.getEmail();
            name = oidcUser.getFullName() != null ? oidcUser.getFullName() : oidcUser.getPreferredUsername();
            sub = oidcUser.getSubject();
            if (name == null && oidcUser.getAttributes().containsKey("name")) {
                name = String.valueOf(oidcUser.getAttributes().get("name"));
            }
        } else if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            Map<String, Object> attributes = oauth2User.getAttributes();
            email = (String) attributes.getOrDefault("email", null);
            name = (String) attributes.getOrDefault("name", attributes.getOrDefault("displayName", null));
            sub = oauth2User.getName();
        }

        if (name == null || name.isBlank()) {
            name = email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : "Zoho User";
        }

        Map<String, Object> userProfile = new LinkedHashMap<>();
        userProfile.put("email", email);
        userProfile.put("name", name);
        userProfile.put("sub", sub);
        userProfile.put("provider", "zoho");

        if (zohoTokenService != null) {
            boolean hasToken = (sub != null && zohoTokenService.hasToken(sub))
                    || (email != null && zohoTokenService.hasToken(email))
                    || zohoTokenService.hasToken(authentication.getName());
            userProfile.put("connected", hasToken);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticated", true);
        response.put("user", userProfile);

        if (email != null && !email.isBlank()) {
            try {
                CustomerResponse customer = customerService.findOrCreateForZohoUser(email, name);
                response.put("customer", customer);
            } catch (Exception ex) {
                log.error("Failed to find or create customer record for Zoho user {}: {}", email, ex.getMessage(), ex);
                response.put("customerError", ex.getMessage());
            }
        }

        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {

        if (authentication != null && zohoTokenService != null) {
            zohoTokenService.removeToken(authentication.getName());
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Cookie cookie = new Cookie("JSESSIONID", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }

    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        return logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    @GetMapping("/zoho/login")
    public void redirectToZoho(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/zoho");
    }

    @GetMapping("/zoho/token/status")
    public ResponseEntity<Map<String, Object>> getTokenStatus(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("connected", false, "error", "Unauthorized"));
        }
        String userId = authentication.getName();
        boolean hasToken = zohoTokenService != null && (zohoTokenService.hasToken(userId));
        return ResponseEntity.ok(Map.of("connected", hasToken, "userId", userId));
    }
}
