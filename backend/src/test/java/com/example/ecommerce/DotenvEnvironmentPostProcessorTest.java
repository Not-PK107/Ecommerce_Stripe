package com.example.ecommerce;

import com.example.ecommerce.config.DotenvEnvironmentPostProcessor;
import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotenvEnvironmentPostProcessorTest {

    private static final String MAIN_CONFIG_LOCATION = "file:src/main/resources/application.properties";

    @Test
    void loadsUpperCaseDotenvAndResolvesApplicationPlaceholders(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                SERVER_PORT=0
                APP_CORS_ALLOWED_ORIGIN=https://shop.example.com
                APP_FRONTEND_URL=https://shop.example.com
                APP_CATALYST_ENABLED=false
                STRIPE_SECRET_KEY=sk_test_dotenv_value
                STRIPE_PUBLISHABLE_KEY=pk_test_dotenv_value
                STRIPE_WEBHOOK_SECRET=whsec_dotenv_value
                ZOHO_ACCOUNTS_URL=https://accounts.zoho.com
                ZOHO_CLIENT_ID=zoho_dotenv_client_id
                ZOHO_CLIENT_SECRET=zoho_dotenv_client_secret
                ZOHO_REDIRECT_URI=https://api.example.com/auth/zoho/callback
                ZOHO_CATALYST_PROJECT_ID=cat_proj_dotenv
                ZOHO_CATALYST_PROJECT_KEY=cat_key_dotenv
                ZOHO_CATALYST_PROJECT_DOMAIN=https://api.catalyst.zoho.com
                ZOHO_CATALYST_ENVIRONMENT=Production
                ZOHO_CATALYST_CLIENT_ID=cat_client_dotenv
                ZOHO_CATALYST_CLIENT_SECRET=cat_secret_dotenv
                ZOHO_CATALYST_REFRESH_TOKEN=cat_refresh_dotenv
                """);

        SpringApplication app = new SpringApplication(ECommerceApplication.class);
        app.setDefaultProperties(Map.of(
                "spring.config.location", MAIN_CONFIG_LOCATION,
                "app.dotenv.path", envFile.toString()
        ));

        try (ConfigurableApplicationContext context = app.run()) {
            ConfigurableEnvironment env = context.getEnvironment();
            assertEquals("0", env.getProperty("server.port"));
            assertEquals("https://shop.example.com", env.getProperty("app.cors.allowed-origin"));
            assertEquals("https://shop.example.com", env.getProperty("app.frontend-url"));
            assertEquals("false", env.getProperty("app.catalyst.enabled"));
            assertEquals("sk_test_dotenv_value", env.getProperty("stripe.secret-key"));
            assertEquals("pk_test_dotenv_value", env.getProperty("stripe.publishable-key"));
            assertEquals("whsec_dotenv_value", env.getProperty("stripe.webhook-secret"));
            assertNotNull(context.getBean(StripeClient.class));

            ClientRegistration zohoReg = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("zoho");
            assertNotNull(zohoReg);
            assertEquals("zoho_dotenv_client_id", zohoReg.getClientId());
            assertEquals("zoho_dotenv_client_secret", zohoReg.getClientSecret());
            assertEquals("https://api.example.com/auth/zoho/callback", zohoReg.getRedirectUri());

            assertEquals("cat_proj_dotenv", env.getProperty("zoho.catalyst.project-id"));
            assertEquals("cat_client_dotenv", env.getProperty("zoho.catalyst.client-id"));
            assertEquals("cat_secret_dotenv", env.getProperty("zoho.catalyst.client-secret"));
            assertEquals("cat_refresh_dotenv", env.getProperty("zoho.catalyst.refresh-token"));
        }
    }

    @Test
    void loadsSpringPropertyStyleDotenvWithLeadingSpacesAndSkipsTemplateLines(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                server.port=0
                app.catalyst.enabled=false
                zoho.client-id= zoho_prop_client_id
                zoho.client-secret= zoho_prop_client_secret
                spring.security.oauth2.client.registration.zoho.client-id=${ZOHO_CLIENT_ID:demo-zoho-client-id}
                spring.security.oauth2.client.registration.zoho.client-secret=${ZOHO_CLIENT_SECRET:demo-zoho-client-secret}
                zoho.catalyst.project-id=cat_proj_prop
                zoho.catalyst.client-id= cat_client_prop
                zoho.catalyst.client-secret=cat_secret_prop
                zoho.catalyst.refresh-token=cat_refresh_prop
                stripe.secret-key= sk_test_prop_value
                stripe.publishable-key= pk_test_prop_value
                stripe.webhook-secret=${STRIPE_WEBHOOK_SECRET:whsec_test_dummy}
                """);

        SpringApplication app = new SpringApplication(ECommerceApplication.class);
        app.setDefaultProperties(Map.of(
                "spring.config.location", MAIN_CONFIG_LOCATION,
                "app.dotenv.path", envFile.toString()
        ));

        try (ConfigurableApplicationContext context = app.run()) {
            ConfigurableEnvironment env = context.getEnvironment();
            assertEquals("sk_test_prop_value", env.getProperty("stripe.secret-key"));
            assertEquals("pk_test_prop_value", env.getProperty("stripe.publishable-key"));
            String webhookSecret = env.getProperty("stripe.webhook-secret");
            assertTrue(webhookSecret == null || webhookSecret.isEmpty(),
                    "Template placeholder line in .env should be skipped rather than exposing dummy secret");

            ClientRegistration zohoReg = context.getBean(ClientRegistrationRepository.class)
                    .findByRegistrationId("zoho");
            assertNotNull(zohoReg);
            assertEquals("zoho_prop_client_id", zohoReg.getClientId());
            assertEquals("zoho_prop_client_secret", zohoReg.getClientSecret());
        }
    }

    @Test
    void externalSystemEnvironmentTakesPrecedenceOverDotenv(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                STRIPE_SECRET_KEY=sk_test_from_dotenv
                ZOHO_CLIENT_ID=zoho_from_dotenv
                """);

        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().replace(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("STRIPE_SECRET_KEY", "sk_live_from_os_env")
                )
        );
        env.getPropertySources().addLast(new org.springframework.core.env.MapPropertySource(
                "testDotenvConfig",
                Map.of("app.dotenv.path", envFile.toString())
        ));

        new DotenvEnvironmentPostProcessor()
                .postProcessEnvironment(env, new SpringApplication(ECommerceApplication.class));

        assertEquals("sk_live_from_os_env", env.getProperty("STRIPE_SECRET_KEY"));
        assertEquals("zoho_from_dotenv", env.getProperty("ZOHO_CLIENT_ID"));
    }

    @Test
    void resolvesBackendDotenvFromProjectRootOrSubdirectoryAndIgnoresFrontendDotenv(@TempDir Path tempDir) throws IOException {
        Path backendDir = Files.createDirectories(tempDir.resolve("backend/src/main"));
        Path frontendDir = Files.createDirectories(tempDir.resolve("frontend/src"));
        Path otherSubDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(tempDir.resolve("backend/pom.xml"), "<project/>");
        Files.writeString(tempDir.resolve("backend/.env"), "STRIPE_SECRET_KEY=sk_test_backend\n");
        Files.writeString(tempDir.resolve("frontend/.env"), """
                VITE_API_BASE_URL=http://localhost:8080
                STRIPE_SECRET_KEY=sk_test_wrong_frontend
                """);

        String originalUserDir = System.getProperty("user.dir");
        try {
            assertResolvesDotenvFrom(tempDir, "sk_test_backend");
            assertResolvesDotenvFrom(tempDir.resolve("backend"), "sk_test_backend");
            assertResolvesDotenvFrom(backendDir, "sk_test_backend");
            assertResolvesDotenvFrom(tempDir.resolve("frontend"), "sk_test_backend");
            assertResolvesDotenvFrom(frontendDir, "sk_test_backend");
            assertResolvesDotenvFrom(otherSubDir, "sk_test_backend");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void neverLoadsFrontendDotenvWhenBackendDotenvIsAbsent(@TempDir Path tempDir) throws IOException {
        Path frontendDir = Files.createDirectories(tempDir.resolve("frontend"));
        Files.writeString(frontendDir.resolve(".env"), "VITE_API_BASE_URL=http://localhost:8080\n");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", frontendDir.toString());
            StandardEnvironment env = new StandardEnvironment();
            new DotenvEnvironmentPostProcessor()
                    .postProcessEnvironment(env, new SpringApplication(ECommerceApplication.class));

            assertFalse(env.getPropertySources().contains(DotenvEnvironmentPostProcessor.DOTENV_PROPERTY_SOURCE_NAME));
            assertNull(env.getProperty("VITE_API_BASE_URL"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void higherPrioritySystemPropertiesAreNotOverwrittenByDotenv(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "stripe.secret-key=sk_test_from_dotenv\n");

        System.setProperty("stripe.secret-key", "sk_live_from_system_property");
        try {
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addLast(new org.springframework.core.env.MapPropertySource(
                    "testDotenvConfig",
                    Map.of("app.dotenv.path", envFile.toString())
            ));
            new DotenvEnvironmentPostProcessor()
                    .postProcessEnvironment(env, new SpringApplication(ECommerceApplication.class));

            assertEquals("sk_live_from_system_property", env.getProperty("stripe.secret-key"));
        } finally {
            System.clearProperty("stripe.secret-key");
        }
    }

    @Test
    void missingDotenvThrowsClearErrorWhenExplicitlyRequired(@TempDir Path tempDir) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new org.springframework.core.env.MapPropertySource(
                "requiredMissingDotenv",
                Map.of(
                        "app.dotenv.required", "true",
                        "app.dotenv.path", tempDir.resolve("missing.env").toString()
                )
        ));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new DotenvEnvironmentPostProcessor()
                        .postProcessEnvironment(env, new SpringApplication(ECommerceApplication.class))
        );
        assertTrue(ex.getMessage().contains(".env"));
    }

    private void assertResolvesDotenvFrom(Path startDir, String expectedKey) {
        StandardEnvironment env = new StandardEnvironment();
        System.setProperty("user.dir", startDir.toString());
        new DotenvEnvironmentPostProcessor()
                .postProcessEnvironment(env, new SpringApplication(ECommerceApplication.class));
        assertNotNull(env.getPropertySources().get(DotenvEnvironmentPostProcessor.DOTENV_PROPERTY_SOURCE_NAME));
        assertEquals(expectedKey, env.getProperty("STRIPE_SECRET_KEY"));
        assertNull(env.getProperty("VITE_API_BASE_URL"));
    }
}
