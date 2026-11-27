package com.example.ecommerce;

import com.catalyst.config.ZCThreadLocal;
import com.example.ecommerce.config.CatalystConfig;
import com.example.ecommerce.config.CatalystRequestContext;
import com.example.ecommerce.config.CatalystRequestFilter;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.api.APIConstants.ZCUserScope;
import com.zc.common.ZCProject;
import com.zc.common.ZCProjectConfig;
import com.zc.component.object.ZCRowObject;
import com.zc.component.object.ZCRowPagedResponse;
import com.zc.component.object.ZCTable;
import com.zc.component.zcql.ZCQL;
import com.zc.exception.ZCServerException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalystConfigAndDataStoreTest {

    @Test
    void initializeProject_ConfiguresProjectIdKeyEnvironmentDomainAndAuth() {
        CatalystConfig configHelper = new CatalystConfig();
        ZCProject project = configHelper.initializeProject(
                "1234567890",
                "test-project-key",
                "https://api.catalyst.zoho.in/",
                "development",
                "cat-client-id",
                "cat-client-secret",
                "cat-refresh-token",
                "https://accounts.zoho.in/"
        );

        assertNotNull(project);
        ZCProjectConfig projectConfig = project.getConfig();
        assertEquals(1234567890L, projectConfig.getProjectId());
        assertEquals("test-project-key", projectConfig.getProjectKey());
        assertEquals("https://api.catalyst.zoho.in", projectConfig.getProjectDomain());
        assertEquals("Development", projectConfig.getEnvironment());
        assertEquals(ZCUserScope.ADMIN, projectConfig.getZcAuth().getScope());
        assertEquals("cat-client-id", projectConfig.getZcAuth().getClientId());
    }

    @Test
    void initializeProject_MissingRequiredCredentials_ThrowsClearError() {
        CatalystConfig configHelper = new CatalystConfig();

        assertThrows(IllegalStateException.class, () -> configHelper.initializeProject(
                "not-a-number", "key", "https://api.catalyst.zoho.in", "Development",
                "cid", "csec", "rtok", "https://accounts.zoho.in"
        ));

        assertThrows(IllegalStateException.class, () -> configHelper.initializeProject(
                "123456", "key", "https://api.catalyst.zoho.in", "Production",
                "", "csec", "rtok", "https://accounts.zoho.in"
        ));

        assertThrows(IllegalStateException.class, () -> configHelper.initializeProject(
                "123456", "key", "https://api.catalyst.zoho.in", "Production",
                "cid", "csec", " ", "https://accounts.zoho.in"
        ));

        ZCProject withoutKey = configHelper.initializeProject(
                "123456", "", "https://api.catalyst.zoho.in", "Development",
                "cid", "csec", "rtok", "https://accounts.zoho.in"
        );
        assertEquals("", withoutKey.getConfig().getProjectKey());
    }

    @Test
    void zcProject_SupportsAppSailHeaderModeWithoutOAuthCredentials() {
        CatalystConfig configHelper = new CatalystConfig();

        assertNull(configHelper.zcProject(
                "appsail", "", "", "https://api.catalyst.zoho.in", "Development",
                "", "", "", "https://accounts.zoho.in"
        ));

        assertNull(configHelper.zcProject(
                "auto", "123456", "", "https://api.catalyst.zoho.in", "Development",
                "", "", "", "https://accounts.zoho.in"
        ));
    }

    @Test
    void catalystRequestFilter_InitializesAndCleansUpAppSailRequestContext() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<ZCProject> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        CatalystRequestFilter filter = new CatalystRequestFilter(emptyProvider, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ZC-Admin-Cred-Type", "token");
        request.addHeader("X-ZC-Admin-Cred-Token", "appsail-admin-token");
        request.addHeader("X-ZC-ProjectId", "89157000000044364");
        request.addHeader("X-ZC-Project-Key", "test-zaid");
        request.addHeader("X-ZC-Project-Domain", "https://api.catalyst.zoho.in");
        request.addHeader("X-ZC-Environment", "Development");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<ZCProject> capturedProject = new AtomicReference<>();
        AtomicReference<Object> capturedUserType = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            capturedProject.set(CatalystRequestContext.getCurrentProject());
            capturedUserType.set(ZCThreadLocal.getValue("user_type"));
        };

        filter.doFilter(request, response, chain);

        assertNotNull(capturedProject.get());
        assertEquals(89157000000044364L, capturedProject.get().getConfig().getProjectId());
        assertEquals("test-zaid", capturedProject.get().getConfig().getProjectKey());
        assertEquals("admin", capturedUserType.get());
        assertNull(CatalystRequestContext.getCurrentProject());
        assertNull(ZCThreadLocal.getValue("user_type"));
    }

    @Test
    void dataStoreService_WhenCatalystDisabled_ThrowsClearError() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ZCProject> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);

        CatalystDataStoreService service = new CatalystDataStoreService(emptyProvider);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.getAllRows("Product"));
        assertTrue(ex.getMessage().contains("app.catalyst.enabled is false"));
    }

    @Test
    void dataStoreService_GetAllRows_PaginatesUntilNoMoreRecords() throws Exception {
        ZCTable mockTable = mock(ZCTable.class);
        ZCQL mockZcql = mock(ZCQL.class);
        CatalystDataStoreService service = testableService(mockTable, mockZcql);

        ZCRowObject row1 = ZCRowObject.getInstance();
        row1.set("ROWID", 1L);
        ZCRowObject row2 = ZCRowObject.getInstance();
        row2.set("ROWID", 2L);

        ZCRowPagedResponse page1 = new ZCRowPagedResponse();
        page1.setRows(List.of(row1));
        page1.setMoreRecordsAvailable(true);
        page1.setNextToken("token-2");

        ZCRowPagedResponse page2 = new ZCRowPagedResponse();
        page2.setRows(List.of(row2));
        page2.setMoreRecordsAvailable(false);
        page2.setNextToken(null);

        when(mockTable.getPagedRows(null, 200)).thenReturn(page1);
        when(mockTable.getPagedRows("token-2", 200)).thenReturn(page2);

        List<ZCRowObject> rows = service.getAllRows("Product");
        assertEquals(2, rows.size());
        assertEquals("1", CatalystDataStoreService.getString(rows.get(0), "ROWID"));
        assertEquals("2", CatalystDataStoreService.getString(rows.get(1), "ROWID"));
    }

    @Test
    void dataStoreService_CrudAndNotFoundHandling() throws Exception {
        ZCTable mockTable = mock(ZCTable.class);
        ZCQL mockZcql = mock(ZCQL.class);
        CatalystDataStoreService service = testableService(mockTable, mockZcql);

        ZCRowObject inserted = ZCRowObject.getInstance();
        inserted.set("ROWID", 55L);
        when(mockTable.insertRow(any(ZCRowObject.class))).thenReturn(inserted);
        ZCRowObject saved = service.insertRow("Product", Map.of("prodName", "Mug", "prodPrice", new BigDecimal("19.50")));
        assertEquals("55", CatalystDataStoreService.getRequiredRowId(saved, "Product"));

        when(mockTable.updateRows(any())).thenReturn(List.of(inserted));
        service.updateRow("Product", "55", Map.of("prodName", "Ceramic Mug"));
        verify(mockTable).updateRows(any());

        when(mockTable.getRow(55L)).thenReturn(inserted);
        assertTrue(service.getRowById("Product", "55").isPresent());
        assertEquals(Optional.empty(), service.getRowById("Product", "non-numeric"));

        when(mockTable.getRow(999L)).thenThrow(new ZCServerException("No such row", "INVALID_ID", 404));
        assertEquals(Optional.empty(), service.getRowById("Product", "999"));

        doThrow(new ZCServerException("No such row", "INVALID_ID", 404)).when(mockTable).deleteRow(999L);
        assertDoesNotThrow(() -> service.deleteRow("Product", "999"));

        ArrayList<ZCRowObject> zcqlRows = new ArrayList<>(List.of(inserted));
        when(mockZcql.executeQuery("SELECT * FROM Product")).thenReturn(zcqlRows);
        assertEquals(1, service.executeQuery("SELECT * FROM Product").size());
    }

    private static CatalystDataStoreService testableService(ZCTable mockTable, ZCQL mockZcql) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ZCProject> provider = mock(ObjectProvider.class);
        return new CatalystDataStoreService(provider) {
            @Override
            protected ZCTable getTable(String tableName) {
                return mockTable;
            }

            @Override
            protected ZCQL getZcql() {
                return mockZcql;
            }
        };
    }
}
