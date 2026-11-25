package com.example.ecommerce;

import com.example.ecommerce.config.CatalystClient;
import com.example.ecommerce.model.Customer;
import com.example.ecommerce.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerRepositoryTest {

    private final CatalystClient catalystClient = mock(CatalystClient.class);
    private final CustomerRepository repository =
            new CustomerRepository(catalystClient, new ObjectMapper());

    @Test
    void insert_SetsRowIdAsCustomerIdFromCatalystResponse() {
        Customer customer = Customer.builder()
                .customerName("Customer")
                .email("customer@example.com")
                .phone("1234567890")
                .address("1 Example Street")
                .build();
        when(catalystClient.insertRows(eq("Customer"), argThat(rows ->
                "Customer".equals(rows.get(0).get("customerName"))
                        && "customer@example.com".equals(rows.get(0).get("email")))))
                .thenReturn("{\"data\":[{\"ROWID\":\"123\"}]}");

        Customer inserted = repository.insert(customer);

        assertEquals("123", inserted.getCustomerId());
        assertEquals("123", inserted.getRowId());
    }

    @Test
    void findByIdAndUpdate_UsesRowIdForLookupAndUpdate() {
        when(catalystClient.getAllRows("Customer")).thenReturn(
                "{\"data\":[{\"ROWID\":\"123\",\"customerName\":\"Customer\","
                        + "\"email\":\"customer@example.com\",\"phone\":\"1234567890\",\"address\":\"1 Example Street\"}]}");

        Customer customer = repository.findById("123");

        assertNotNull(customer);
        assertEquals("123", customer.getCustomerId());
        assertEquals("123", customer.getRowId());
        customer.setCustomerName("Updated Customer");
        repository.update(customer);

        verify(catalystClient).updateRows(eq("Customer"), argThat(rows -> {
            Map<String, Object> row = rows.get(0);
            return Long.valueOf(123).equals(row.get("ROWID"))
                    && "Updated Customer".equals(row.get("customerName"));
        }));
    }

    @Test
    void deleteById_DeletesCatalystRowMatchingRowId() {
        when(catalystClient.getAllRows("Customer")).thenReturn(
                "{\"data\":[{\"ROWID\":\"123\",\"customerName\":\"Customer\"}]}");

        repository.deleteById("123");

        verify(catalystClient).deleteRow("Customer", "123");
    }
}

