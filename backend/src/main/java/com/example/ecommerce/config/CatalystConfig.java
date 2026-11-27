package com.example.ecommerce.config;

import com.catalyst.config.ZCThreadLocal;
import com.zc.api.APIConstants.ZCUserScope;
import com.zc.auth.ZCAuth;
import com.zc.common.ZCProject;
import com.zc.common.ZCProjectConfig;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Configures and initializes the official Zoho Catalyst Java SDK for both OAuth refresh-token
 * deployments and Catalyst AppSail request-header authentication ({@code AuthHeaderProvider}).
 * Only active when {@code app.catalyst.enabled=true}.
 */
@Configuration
public class CatalystConfig {

    private static final Logger logger = LoggerFactory.getLogger(CatalystConfig.class);
    private static final String CATALYST_ACCOUNTS_URL_ENV = "X_ZOHO_CATALYST_ACCOUNTS_URL";
    private static final String CATALYST_CONSOLE_URL_ENV = "X_ZOHO_CATALYST_CONSOLE_URL";
    private static final String DEFAULT_CATALYST_DOMAIN = "https://api.catalyst.zoho.in";
    private static final String DEFAULT_ACCOUNTS_URL = "https://accounts.zoho.in";

    @Bean
    @ConditionalOnProperty(name = "app.catalyst.enabled", havingValue = "true")
    public ZCProject zcProject(
            @Value("${zoho.catalyst.auth-mode:auto}") String authMode,
            @Value("${zoho.catalyst.project-id:}") String projectId,
            @Value("${zoho.catalyst.project-key:}") String projectKey,
            @Value("${zoho.catalyst.project-domain:" + DEFAULT_CATALYST_DOMAIN + "}") String projectDomain,
            @Value("${zoho.catalyst.environment:Development}") String environment,
            @Value("${zoho.catalyst.client-id:}") String clientId,
            @Value("${zoho.catalyst.client-secret:}") String clientSecret,
            @Value("${zoho.catalyst.refresh-token:}") String refreshToken,
            @Value("${zoho.accounts.base-url:" + DEFAULT_ACCOUNTS_URL + "}") String accountsBaseUrl) {

        String normalizedDomain = stripTrailingSlashes(
                isBlank(projectDomain) ? DEFAULT_CATALYST_DOMAIN : projectDomain.trim());
        String normalizedAccountsUrl = stripTrailingSlashes(
                isBlank(accountsBaseUrl) ? DEFAULT_ACCOUNTS_URL : accountsBaseUrl.trim());
        configureUrlPropertiesIfUnset(normalizedDomain, normalizedAccountsUrl);

        String mode = isBlank(authMode) ? "auto" : authMode.trim().toLowerCase();
        if ("appsail".equals(mode)) {
            logger.info("Catalyst SDK configured in AppSail header mode (per-request AuthHeaderProvider)");
            return null;
        }

        boolean hasOAuthCreds = !isBlank(projectId)
                && !isBlank(clientId)
                && !isBlank(clientSecret)
                && !isBlank(refreshToken);
        if ("auto".equals(mode) && !hasOAuthCreds) {
            logger.info("Catalyst OAuth credentials not fully configured; using AppSail per-request AuthHeaderProvider mode");
            return null;
        }

        return initializeProject(
                projectId,
                projectKey,
                normalizedDomain,
                environment,
                clientId,
                clientSecret,
                refreshToken,
                normalizedAccountsUrl
        );
    }

    @Bean
    @ConditionalOnProperty(name = "app.catalyst.enabled", havingValue = "true")
    public FilterRegistrationBean<CatalystRequestFilter> catalystRequestFilter(
            ObjectProvider<ZCProject> zcProjectProvider,
            @Value("${zoho.catalyst.auth-mode:auto}") String authMode) {

        boolean preferAppSail = "appsail".equalsIgnoreCase(authMode == null ? "" : authMode.trim());
        FilterRegistrationBean<CatalystRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CatalystRequestFilter(zcProjectProvider, preferAppSail));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Builds a {@link ZCProjectConfig} with OAuth refresh-token credentials and initializes {@link ZCProject}.
     */
    public ZCProject initializeProject(
            String projectId,
            String projectKey,
            String projectDomain,
            String environment,
            String clientId,
            String clientSecret,
            String refreshToken,
            String accountsBaseUrl) {

        long parsedProjectId = parseProjectId(projectId);
        String normalizedProjectKey = isBlank(projectKey) ? "" : projectKey.trim();
        String requiredClientId = requireNonBlank(clientId, "zoho.catalyst.client-id (ZOHO_CATALYST_CLIENT_ID)");
        String requiredClientSecret = requireNonBlank(clientSecret, "zoho.catalyst.client-secret (ZOHO_CATALYST_CLIENT_SECRET)");
        String requiredRefreshToken = requireNonBlank(refreshToken, "zoho.catalyst.refresh-token (ZOHO_CATALYST_REFRESH_TOKEN)");

        String normalizedDomain = stripTrailingSlashes(
                isBlank(projectDomain) ? DEFAULT_CATALYST_DOMAIN : projectDomain.trim());
        String normalizedEnvironment = normalizeEnvironment(environment);
        String normalizedAccountsUrl = stripTrailingSlashes(
                isBlank(accountsBaseUrl) ? DEFAULT_ACCOUNTS_URL : accountsBaseUrl.trim());

        configureUrlPropertiesIfUnset(normalizedDomain, normalizedAccountsUrl);

        ZCProjectConfig config = buildProjectConfig(
                parsedProjectId,
                normalizedProjectKey,
                normalizedDomain,
                normalizedEnvironment,
                requiredClientId,
                requiredClientSecret,
                requiredRefreshToken
        );

        ZCProject project = ZCProject.initProject(config, true, String.valueOf(parsedProjectId));
        ZCThreadLocal.putValue("user_type", "admin");
        logger.info("Initialized Zoho Catalyst Java SDK for projectId={} environment={} domain={}",
                parsedProjectId, normalizedEnvironment, normalizedDomain);
        return project;
    }

    @SuppressWarnings("unchecked")
    ZCProjectConfig buildProjectConfig(
            long projectId,
            String projectKey,
            String projectDomain,
            String environment,
            String clientId,
            String clientSecret,
            String refreshToken) {

        JSONObject oAuthParams = new JSONObject();
        oAuthParams.put("client_id", clientId);
        oAuthParams.put("client_secret", clientSecret);
        oAuthParams.put("refresh_token", refreshToken);
        oAuthParams.put("grant_type", "refresh_token");

        ZCAuth auth = ZCAuth.getInstance(oAuthParams);
        auth.setScope(ZCUserScope.ADMIN);

        return ZCProjectConfig.newBuilder()
                .setProjectId(projectId)
                .setProjectKey(projectKey)
                .setZcAuth(auth)
                .setProjectDomain(projectDomain)
                .setEnvironment(environment)
                .build();
    }

    private static void configureUrlPropertiesIfUnset(String projectDomain, String accountsUrl) {
        setSystemPropertyIfUnset(CATALYST_ACCOUNTS_URL_ENV, accountsUrl);
        setSystemPropertyIfUnset(CATALYST_CONSOLE_URL_ENV, projectDomain);
    }

    private static void setSystemPropertyIfUnset(String key, String value) {
        String envValue = System.getenv(key);
        String propValue = System.getProperty(key);
        if (isBlank(envValue) && isBlank(propValue)) {
            System.setProperty(key, value);
        }
    }

    private static long parseProjectId(String projectId) {
        String trimmed = requireNonBlank(projectId, "zoho.catalyst.project-id (ZOHO_CATALYST_PROJECT_ID)");
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "Invalid zoho.catalyst.project-id value '" + trimmed + "': expected numeric Catalyst project ID",
                    ex);
        }
    }

    private static String normalizeEnvironment(String environment) {
        if (isBlank(environment)) {
            return "Development";
        }
        String trimmed = environment.trim();
        return "development".equalsIgnoreCase(trimmed) ? "Development" : trimmed;
    }

    private static String requireNonBlank(String value, String propertyName) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    "Missing required Catalyst SDK configuration property: " + propertyName);
        }
        return value.trim();
    }

    private static String stripTrailingSlashes(String value) {
        return value.replaceAll("/+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
