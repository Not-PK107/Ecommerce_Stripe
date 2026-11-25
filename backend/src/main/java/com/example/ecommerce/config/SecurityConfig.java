package com.example.ecommerce.config;

import com.example.ecommerce.service.ZohoTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final ZohoTokenService zohoTokenService;

    public SecurityConfig(ZohoTokenService zohoTokenService) {
        this.zohoTokenService = zohoTokenService;
    }

    @Value("${app.frontend-url:http://localhost:2000}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origin:http://localhost:2000}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizedClientService authorizedClientService) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/**",
                                "/auth/**",
                                "/oauth2/**",
                                "/login/**",
                                "/error",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/auth/zoho/callback")
                        )
                        .successHandler((request, response, authentication) -> {
                            log.info("Zoho OAuth authentication successful for user: {}", authentication.getName());

                            try {
                                if (authorizedClientService != null && authentication instanceof OAuth2AuthenticationToken oauthToken) {
                                    OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                                            oauthToken.getAuthorizedClientRegistrationId(),
                                            oauthToken.getName()
                                    );
                                    if (authorizedClient != null) {
                                        String accessToken = authorizedClient.getAccessToken() != null
                                                ? authorizedClient.getAccessToken().getTokenValue()
                                                : null;
                                        String refreshToken = authorizedClient.getRefreshToken() != null
                                                ? authorizedClient.getRefreshToken().getTokenValue()
                                                : null;
                                        Instant expiresAt = authorizedClient.getAccessToken() != null
                                                ? authorizedClient.getAccessToken().getExpiresAt()
                                                : null;
                                        String scope = authorizedClient.getAccessToken() != null && authorizedClient.getAccessToken().getScopes() != null
                                                ? String.join(",", authorizedClient.getAccessToken().getScopes())
                                                : "openid,email,profile";

                                        String userEmail = authentication.getName();
                                        if (authentication.getPrincipal() instanceof OidcUser oidcUser && oidcUser.getEmail() != null) {
                                            userEmail = oidcUser.getEmail();
                                        }

                                        zohoTokenService.saveToken(
                                                authentication.getName(),
                                                userEmail,
                                                accessToken,
                                                refreshToken,
                                                expiresAt,
                                                scope
                                        );
                                    }
                                }
                            } catch (Exception ex) {
                                log.warn("Could not cache OAuth2 tokens in successHandler: {}", ex.getMessage());
                            }

                            String targetUrl = frontendUrl.replaceAll("/+$", "") + "/?zoho_login=success";
                            response.sendRedirect(targetUrl);
                        })
                        .failureHandler((request, response, exception) -> {
                            log.error("Zoho OAuth authentication failed: {}", exception.getMessage(), exception);
                            String encodedMsg = URLEncoder.encode(
                                    exception.getMessage() != null ? exception.getMessage() : "Authentication failed",
                                    StandardCharsets.UTF_8
                            );
                            String targetUrl = frontendUrl.replaceAll("/+$", "") + "/?zoho_login=error&error=" + encodedMsg;
                            response.sendRedirect(targetUrl);
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                zohoTokenService.removeToken(authentication.getName());
                            }
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\":true,\"message\":\"Logged out successfully\"}");
                        })
                );

        return http.build();
    }
}
