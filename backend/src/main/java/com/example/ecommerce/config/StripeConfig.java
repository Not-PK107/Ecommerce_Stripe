
package com.example.ecommerce.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "stripe.secret-key")
public class StripeConfig {

    @Bean
    public StripeClient stripeClient(
            @Value("${stripe.secret-key}") String secretKey) {

        return new StripeClient(secretKey);
    }


}
