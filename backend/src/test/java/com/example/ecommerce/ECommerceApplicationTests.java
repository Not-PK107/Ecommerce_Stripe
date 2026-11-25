package com.example.ecommerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "SERVER_PORT=0",
        "APP_CATALYST_ENABLED=false",
        "STRIPE_SECRET_KEY=sk_test_unit_placeholder",
        "STRIPE_PUBLISHABLE_KEY=pk_test_unit_placeholder",
        "STRIPE_WEBHOOK_SECRET=whsec_test_unit_placeholder",
        "ZOHO_CLIENT_ID=test-zoho-client-id",
        "ZOHO_CLIENT_SECRET=test-zoho-client-secret",
        "ZOHO_CATALYST_PROJECT_ID=test-catalyst-project-id",
        "ZOHO_CATALYST_PROJECT_KEY=test-catalyst-project-key",
        "ZOHO_CATALYST_CLIENT_ID=test-catalyst-client-id",
        "ZOHO_CATALYST_CLIENT_SECRET=test-catalyst-client-secret",
        "ZOHO_CATALYST_REFRESH_TOKEN=test-catalyst-refresh-token"
})
class ECommerceApplicationTests {

    @Test
    void contextLoads() {
    }

}
