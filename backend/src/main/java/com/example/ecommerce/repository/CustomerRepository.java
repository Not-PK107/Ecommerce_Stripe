package com.example.ecommerce.repository;

import com.example.ecommerce.model.Customer;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Repository
public class CustomerRepository {

    private static final Logger logger = LoggerFactory.getLogger(CustomerRepository.class);
    private static final String TABLE_NAME = "Customer";

    private final CatalystDataStoreService dataStore;

    public CustomerRepository(CatalystDataStoreService dataStore) {
        this.dataStore = dataStore;
    }

    public Customer insert(Customer customer) {
        try {
            ZCRowObject inserted = dataStore.insertRow(TABLE_NAME, toRow(customer));
            String rowId = CatalystDataStoreService.getRequiredRowId(inserted, TABLE_NAME);
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
            dataStore.updateRow(TABLE_NAME, customer.getRowId(), toRow(customer));
            return customer;
        } catch (Exception ex) {
            throw operationFailed("update customer", ex);
        }
    }

    public Customer findById(String customerId) {
        try {
            return dataStore.getRowById(TABLE_NAME, customerId)
                    .map(this::toCustomer)
                    .orElse(null);
        } catch (Exception ex) {
            throw operationFailed("find customer", ex);
        }
    }

    public Customer findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return findAll().stream()
                .filter(customer -> customer.getEmail() != null
                        && normalized.equalsIgnoreCase(customer.getEmail().trim()))
                .findFirst()
                .orElse(null);
    }

    public List<Customer> findAll() {
        try {
            List<ZCRowObject> rows = dataStore.getAllRows(TABLE_NAME);
            List<Customer> customers = new ArrayList<>();
            for (ZCRowObject row : rows) {
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
            dataStore.deleteRow(TABLE_NAME, customer.getRowId());
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

    private Customer toCustomer(ZCRowObject row) {
        String rowId = CatalystDataStoreService.getString(row, "ROWID");
        return Customer.builder()
                .customerId(rowId)
                .rowId(rowId)
                .customerName(CatalystDataStoreService.getString(row, "customerName"))
                .email(CatalystDataStoreService.getString(row, "email"))
                .phone(CatalystDataStoreService.getString(row, "phone"))
                .address(CatalystDataStoreService.getString(row, "address"))
                .createdAt(CatalystDataStoreService.getString(row, "createdAt"))
                .build();
    }

    private IllegalStateException operationFailed(String operation, Exception cause) {
        logger.error("Catalyst {} operation failed for table '{}'", operation, TABLE_NAME, cause);
        return new IllegalStateException("Catalyst customer operation failed", cause);
    }
}
