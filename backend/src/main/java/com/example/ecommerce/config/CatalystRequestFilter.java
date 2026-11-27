package com.example.ecommerce.config;

import com.catalyst.config.ZCThreadLocal;
import com.zc.api.APIConstants.ZCUserScope;
import com.zc.auth.AuthHeaderProvider;
import com.zc.auth.CatalystSDK;
import com.zc.common.ZCProject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds Catalyst SDK thread-local context for incoming HTTP requests, supporting both AppSail
 * header-based initialization ({@link AuthHeaderProvider}) and pre-configured OAuth mode.
 */
public class CatalystRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CatalystRequestFilter.class);

    private static final String HEADER_ADMIN_CRED_TYPE = "X-ZC-Admin-Cred-Type";
    private static final String HEADER_ADMIN_CRED_TOKEN = "X-ZC-Admin-Cred-Token";
    private static final String HEADER_PROJECT_ID = "X-ZC-ProjectId";

    private final ObjectProvider<ZCProject> oAuthProjectProvider;
    private final boolean preferAppSailHeaders;

    public CatalystRequestFilter(ObjectProvider<ZCProject> oAuthProjectProvider, boolean preferAppSailHeaders) {
        this.oAuthProjectProvider = oAuthProjectProvider;
        this.preferAppSailHeaders = preferAppSailHeaders;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        boolean hasOAuthProject = oAuthProjectProvider.getIfAvailable() != null;
        boolean hasAppSailHeaders = hasAppSailAuthHeaders(request);

        try {
            if (hasAppSailHeaders && (preferAppSailHeaders || !hasOAuthProject)) {
                CatalystSDK.init((AuthHeaderProvider) request::getHeader);
                ZCProject requestProject = ZCProject.initProject("", ZCUserScope.ADMIN);
                CatalystRequestContext.setCurrentProject(requestProject);
                if (ZCThreadLocal.getValue("user_type") == null) {
                    ZCThreadLocal.putValue("user_type", "admin");
                }
            } else if (hasOAuthProject) {
                ZCThreadLocal.putValue("user_type", "admin");
            }
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            if (hasAppSailHeaders && (preferAppSailHeaders || !hasOAuthProject)) {
                logger.error("Failed to initialize Catalyst SDK from AppSail request headers", ex);
            }
            if (ex instanceof ServletException se) {
                throw se;
            }
            if (ex instanceof IOException ioe) {
                throw ioe;
            }
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new ServletException("Catalyst request initialization failed", ex);
        } finally {
            CatalystRequestContext.clear();
            ZCThreadLocal.clearAllKeys();
        }
    }

    static boolean hasAppSailAuthHeaders(HttpServletRequest request) {
        return hasText(request.getHeader(HEADER_ADMIN_CRED_TYPE))
                && hasText(request.getHeader(HEADER_ADMIN_CRED_TOKEN))
                && hasText(request.getHeader(HEADER_PROJECT_ID));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
