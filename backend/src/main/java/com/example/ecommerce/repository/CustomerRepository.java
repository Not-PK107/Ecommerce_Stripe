package com.example.ecommerce.repository;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class CustomerRepository {

    private static final Logger logger = LoggerFactory.getLogger(CustomerRepository.class);
    private static final String TABLE_NAME = "Customer";

    private final CatalystClient catalystClient;
    private final ObjectMapper objectMapper;

    public CustomerRepository(CatalystClient catalystClient, ObjectMapper objectMapper) {
        this.catalystClient = catalystClient;
        this.objectMapper = objectMapper;
    }

    public Customer insert(Customer customer) {
        try {
            String response = catalystClient.insertRows(
                    TABLE_NAME,
                    List.of(toRow(customer))
            );

            JsonNode rows = responseRows(response);

            if (rows.isEmpty() || !rows.get(0).hasNonNull("ROWID")) {
                throw new IllegalStateException(
                        "Catalyst insert response did not contain a customer ROWID"
                );
            }

            String rowId = rows.get(0).get("ROWID").asText();

            customer.setRowId(rowId);
            customer.setCustomerId(rowId);

            return customer;

        } catch (Exception ex) {
            throw operationFailed("create customer", ex);
        }
    }

    public Customer update(Customer customer) {
        try {
            if (customer.getRowId() == null || customer.getRowId().isBlank()) {
                throw new IllegalStateException("Catalyst ROWID is required to update a customer");
            }
            Map<String, Object> row = toRow(customer);
            row.put("ROWID", Long.parseLong(customer.getRowId()));
            catalystClient.updateRows(TABLE_NAME, List.of(row));
            return customer;
        } catch (Exception ex) {
            throw operationFailed("update customer", ex);
        }
    }

    public Customer findById(String customerId) {
        return findAll().stream()
                .filter(customer -> customerId.equals(customer.getCustomerId()))
                .findFirst()
                .orElse(null);
    }

    public Customer findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        return findAll().stream()
                .filter(customer -> customer.getEmail() != null && normalized.equalsIgnoreCase(customer.getEmail().trim()))
                .findFirst()
                .orElse(null);
    }

    public List<Customer> findAll() {
        try {
            JsonNode rows = responseRows(catalystClient.getAllRows(TABLE_NAME));
            List<Customer> customers = new ArrayList<>();
            for (JsonNode row : rows) {
                customers.add(toCustomer(row));
            }
            return customers;
        } catch (Exception ex) {
            throw operationFailed("list customers", ex);
        }
    }

    public boolean existsByEmail(String email, String excludedCustomerId) {
        return findAll().stream()
                .anyMatch(customer -> email.equalsIgnoreCase(customer.getEmail())
                        && !Objects.equals(customer.getCustomerId(), excludedCustomerId));
    }

    public void deleteById(String customerId) {
        try {
            Customer customer = findById(customerId);
            if (customer == null) {
                return;
            }
            if (customer.getRowId() == null || customer.getRowId().isBlank()) {
                throw new IllegalStateException("Catalyst ROWID is required to delete a customer");
            }
            catalystClient.deleteRow(TABLE_NAME, customer.getRowId());
        } catch (Exception ex) {
            throw operationFailed("delete customer", ex);
        }
    }

    private Map<String, Object> toRow(Customer customer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customerName", customer.getCustomerName());
        row.put("email", customer.getEmail());
        row.put("phone", customer.getPhone());
        if (customer.getCreatedAt() != null) {
            row.put("createdAt", customer.getCreatedAt());
        }

        row.put("address", customer.getAddress());
        return row;
    }

    private Customer toCustomer(JsonNode row) {
        return Customer.builder()
                .customerId(text(row, "ROWID"))
                .rowId(text(row, "ROWID"))
                .customerName(text(row, "customerName"))
                .email(text(row, "email"))
                .phone(text(row, "phone"))
                .address(text(row, "address"))
                .createdAt(text(row, "createdAt"))
                .build();
    }

    private JsonNode responseRows(String response) throws Exception {
        JsonNode rows = objectMapper.readTree(response).path("data");
        if (!rows.isArray()) {
            throw new IllegalStateException("Invalid Catalyst Customer response");
        }
        return rows;
    }

    private String text(JsonNode row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private IllegalStateException operationFailed(String operation, Exception cause) {
        logger.error("Catalyst {} operation failed for table '{}'", operation, TABLE_NAME, cause);
        return new IllegalStateException("Catalyst customer operation failed", cause);
    }
}