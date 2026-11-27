package com.example.ecommerce.service;

import com.catalyst.config.ZCThreadLocal;
import com.example.ecommerce.config.CatalystRequestContext;
import com.zc.common.ZCProject;
import com.zc.component.object.ZCObject;
import com.zc.component.object.ZCRowObject;
import com.zc.component.object.ZCRowPagedResponse;
import com.zc.component.object.ZCTable;
import com.zc.component.zcql.ZCQL;
import com.zc.exception.ZCException;
import com.zc.exception.ZCServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gateway wrapping the official Zoho Catalyst Java SDK DataStore ({@link ZCObject}, {@link ZCTable},
 * {@link ZCRowObject}) and {@link ZCQL} operations.
 */
@Service
public class CatalystDataStoreService {

    private static final Logger logger = LoggerFactory.getLogger(CatalystDataStoreService.class);
    private static final int DEFAULT_PAGE_SIZE = 200;

    private final ObjectProvider<ZCProject> zcProjectProvider;

    public CatalystDataStoreService(ObjectProvider<ZCProject> zcProjectProvider) {
        this.zcProjectProvider = zcProjectProvider;
    }

    /**
     * Fetches all rows from a Catalyst DataStore table using SDK pagination ({@code getPagedRows}).
     */
    public List<ZCRowObject> getAllRows(String tableName) {
        ZCTable table = getTable(tableName);
        try {
            List<ZCRowObject> allRows = new ArrayList<>();
            String nextToken = null;
            ZCRowPagedResponse page;
            do {
                page = table.getPagedRows(nextToken, DEFAULT_PAGE_SIZE);
                if (page != null && page.getRows() != null) {
                    allRows.addAll(page.getRows());
                }
                nextToken = page != null ? page.getNextToken() : null;
            } while (page != null && page.moreRecordsAvailable());
            return allRows;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fetch rows from Catalyst table '" + tableName + "'", ex);
        }
    }

    /**
     * Fetches a single row by its numeric Catalyst {@code ROWID}, returning empty if absent or non-numeric.
     */
    public Optional<ZCRowObject> getRowById(String tableName, String rowId) {
        if (rowId == null || rowId.isBlank()) {
            return Optional.empty();
        }
        Long parsedId;
        try {
            parsedId = Long.parseLong(rowId.trim());
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        ZCTable table = getTable(tableName);
        try {
            ZCRowObject row = table.getRow(parsedId);
            return Optional.ofNullable(row);
        } catch (Exception ex) {
            if (isNotFoundException(ex)) {
                return Optional.empty();
            }
            throw new IllegalStateException(
                    "Failed to fetch row " + rowId + " from Catalyst table '" + tableName + "'", ex);
        }
    }

    /**
     * Inserts a single row into a Catalyst DataStore table and returns the inserted {@link ZCRowObject}.
     */
    public ZCRowObject insertRow(String tableName, Map<String, Object> fields) {
        ZCTable table = getTable(tableName);
        try {
            ZCRowObject row = toRowObject(fields);
            ZCRowObject inserted = table.insertRow(row);
            if (inserted == null) {
                throw new IllegalStateException("Catalyst SDK insertRow returned null for table '" + tableName + "'");
            }
            return inserted;
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("Failed to insert row into Catalyst table '" + tableName + "'", ex);
        }
    }

    /**
     * Inserts multiple rows into a Catalyst DataStore table in bulk.
     */
    public List<ZCRowObject> insertRows(String tableName, List<Map<String, Object>> rowsFields) {
        if (rowsFields == null || rowsFields.isEmpty()) {
            return List.of();
        }
        ZCTable table = getTable(tableName);
        try {
            List<ZCRowObject> rowObjects = rowsFields.stream()
                    .map(CatalystDataStoreService::toRowObject)
                    .toList();
            List<ZCRowObject> inserted = table.insertRows(rowObjects);
            return inserted != null ? inserted : List.of();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to insert rows into Catalyst table '" + tableName + "'", ex);
        }
    }

    /**
     * Updates a single row identified by {@code rowId} in a Catalyst DataStore table.
     */
    public ZCRowObject updateRow(String tableName, String rowId, Map<String, Object> fields) {
        long parsedRowId = parseRequiredRowId(rowId, tableName);
        ZCRowObject row = toRowObject(fields);
        row.set("ROWID", parsedRowId);
        List<ZCRowObject> updated = updateRowObjects(tableName, List.of(row));
        return updated.isEmpty() ? row : updated.get(0);
    }

    /**
     * Updates multiple rows in a Catalyst DataStore table. Each map must include {@code ROWID}.
     */
    public List<ZCRowObject> updateRows(String tableName, List<Map<String, Object>> rowsFields) {
        if (rowsFields == null || rowsFields.isEmpty()) {
            return List.of();
        }
        List<ZCRowObject> rowObjects = new ArrayList<>(rowsFields.size());
        for (Map<String, Object> fields : rowsFields) {
            ZCRowObject row = toRowObject(fields);
            Object rawRowId = fields.get("ROWID");
            if (rawRowId == null) {
                throw new IllegalArgumentException("ROWID is required when updating Catalyst table '" + tableName + "'");
            }
            if (!(rawRowId instanceof Long)) {
                row.set("ROWID", parseRequiredRowId(String.valueOf(rawRowId), tableName));
            }
            rowObjects.add(row);
        }
        return updateRowObjects(tableName, rowObjects);
    }

    /**
     * Deletes a row by {@code ROWID} from a Catalyst DataStore table, treating already-deleted rows idempotently.
     */
    public void deleteRow(String tableName, String rowId) {
        long parsedRowId = parseRequiredRowId(rowId, tableName);
        ZCTable table = getTable(tableName);
        try {
            table.deleteRow(parsedRowId);
        } catch (Exception ex) {
            if (isNotFoundException(ex)) {
                logger.info("Row {} in Catalyst table '{}' is already absent or deleted", rowId, tableName);
                return;
            }
            throw new IllegalStateException(
                    "Failed to delete row " + rowId + " from Catalyst table '" + tableName + "'", ex);
        }
    }

    /**
     * Executes a ZCQL query against the initialized Catalyst project.
     */
    public List<ZCRowObject> executeQuery(String zcqlQuery) {
        ZCQL zcql = getZcql();
        try {
            List<ZCRowObject> results = zcql.executeQuery(zcqlQuery);
            return results != null ? results : List.of();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to execute Catalyst ZCQL query", ex);
        }
    }

    private List<ZCRowObject> updateRowObjects(String tableName, List<ZCRowObject> rowObjects) {
        ZCTable table = getTable(tableName);
        try {
            List<ZCRowObject> updated = table.updateRows(rowObjects);
            return updated != null ? updated : List.of();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to update rows in Catalyst table '" + tableName + "'", ex);
        }
    }

    protected ZCTable getTable(String tableName) {
        return ZCObject.getInstance(requireProject()).getTableInstance(tableName);
    }

    protected ZCQL getZcql() {
        return ZCQL.getInstance(requireProject());
    }

    private ZCProject requireProject() {
        ZCProject project = CatalystRequestContext.getCurrentProject();
        if (project == null) {
            project = zcProjectProvider.getIfAvailable();
        }
        if (project == null) {
            throw new IllegalStateException(
                    "Catalyst SDK is not initialized (app.catalyst.enabled is false or neither OAuth credentials nor AppSail request headers were provided)");
        }
        if (ZCThreadLocal.getValue("user_type") == null) {
            ZCThreadLocal.putValue("user_type", "admin");
        }
        return project;
    }

    private static long parseRequiredRowId(String rowId, String tableName) {
        if (rowId == null || rowId.isBlank()) {
            throw new IllegalArgumentException(
                    "Catalyst ROWID is required for operation on table '" + tableName + "'");
        }
        try {
            return Long.parseLong(rowId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Invalid Catalyst ROWID '" + rowId + "' for table '" + tableName + "'", ex);
        }
    }

    private static boolean isNotFoundException(Throwable ex) {
        if (ex instanceof ZCServerException serverEx) {
            if (serverEx.getStatus() == 404) {
                return true;
            }
            String code = serverEx.getCode();
            if ("INVALID_ID".equalsIgnoreCase(code)) {
                return true;
            }
            String msg = serverEx.getErrorMsg();
            if (msg != null && (msg.contains("INVALID_ID") || msg.contains("No such row"))) {
                return true;
            }
        }
        if (ex instanceof ZCException zcEx) {
            if (zcEx.getStatus() == 404 || "INVALID_ID".equalsIgnoreCase(zcEx.getCode())) {
                return true;
            }
            String msg = zcEx.getErrorMsg();
            if (msg != null && (msg.contains("INVALID_ID") || msg.contains("No such row"))) {
                return true;
            }
        }
        String message = ex.getMessage();
        return message != null && (message.contains("INVALID_ID") || message.contains("No such row"));
    }

    public static ZCRowObject toRowObject(Map<String, Object> fields) {
        ZCRowObject row = ZCRowObject.getInstance();
        if (fields != null) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                row.set(entry.getKey(), entry.getValue());
            }
        }
        return row;
    }

    public static String getString(ZCRowObject row, String field) {
        if (row == null || field == null) {
            return null;
        }
        Object value = row.get(field);
        return value == null ? null : String.valueOf(value);
    }

    public static String getRequiredRowId(ZCRowObject row, String tableName) {
        String rowId = getString(row, "ROWID");
        if (rowId == null || rowId.isBlank()) {
            throw new IllegalStateException(
                    "Catalyst response for table '" + tableName + "' did not contain ROWID");
        }
        return rowId;
    }

    public static BigDecimal getBigDecimal(ZCRowObject row, String field) {
        BigDecimal value = getNullableBigDecimal(row, field);
        return value != null ? value : BigDecimal.ZERO;
    }

    public static BigDecimal getNullableBigDecimal(ZCRowObject row, String field) {
        if (row == null || field == null) {
            return null;
        }
        Object value = row.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : new BigDecimal(str);
    }

    public static Long getLong(ZCRowObject row, String field) {
        if (row == null || field == null) {
            return null;
        }
        Object value = row.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : Long.parseLong(str);
    }

    public static int getInt(ZCRowObject row, String field, int defaultValue) {
        if (row == null || field == null) {
            return defaultValue;
        }
        Object value = row.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number num) {
            return num.intValue();
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return defaultValue;
        }
        return new BigDecimal(str).intValue();
    }

    public static boolean getBoolean(ZCRowObject row, String field, boolean defaultValue) {
        if (row == null || field == null) {
            return defaultValue;
        }
        Object value = row.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
