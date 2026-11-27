package com.example.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend-url:http://localhost:2000}")
    private String frontendUrl;

    @Value("${app.cors.allowed-origin:http://localhost:2000}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/**",
                                "/error",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = resolveAllowedOrigins(allowedOrigin, frontendUrl);

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static List<String> resolveAllowedOrigins(String allowedOrigin, String frontendUrl) {
        List<String> origins = new ArrayList<>();
        addCommaSeparatedOrigins(origins, allowedOrigin);
        addCommaSeparatedOrigins(origins, frontendUrl);
        if (!origins.contains("http://localhost:2000")) {
            origins.add("http://localhost:2000");
        }
        return origins;
    }

    private static void addCommaSeparatedOrigins(List<String> target, String rawOrigins) {
        if (rawOrigins == null || rawOrigins.isBlank()) {
            return;
        }
        for (String part : rawOrigins.split(",")) {
            String normalized = part.trim().replaceAll("/+$", "");
            if (!normalized.isBlank() && !target.contains(normalized)) {
                target.add(normalized);
            }
        }
    }
}
