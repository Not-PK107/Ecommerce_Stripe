package com.example.ecommerce;

import com.example.ecommerce.model.Customer;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.service.CatalystDataStoreService;
import com.zc.component.object.ZCRowObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerRepositoryTest {

    private final CatalystDataStoreService dataStore = mock(CatalystDataStoreService.class);
    private final CustomerRepository repository = new CustomerRepository(dataStore);

    @Test
    void insert_SetsRowIdAsCustomerIdFromCatalystResponse() {
        Customer customer = Customer.builder()
                .customerName("Customer")
                .email("customer@example.com")
                .phone("1234567890")
                .address("1 Example Street")
                .build();
        ZCRowObject insertedRow = ZCRowObject.getInstance();
        insertedRow.set("ROWID", 123L);
        when(dataStore.insertRow(eq("Customer"), argThat(row ->
                "Customer".equals(row.get("customerName"))
                        && "customer@example.com".equals(row.get("email")))))
                .thenReturn(insertedRow);

        Customer inserted = repository.insert(customer);

        assertEquals("123", inserted.getCustomerId());
        assertEquals("123", inserted.getRowId());
    }

    @Test
    void findByIdAndUpdate_UsesRowIdForLookupAndUpdate() {
        ZCRowObject row = ZCRowObject.getInstance();
        row.set("ROWID", 123L);
        row.set("customerName", "Customer");
        row.set("email", "customer@example.com");
        row.set("phone", "1234567890");
        row.set("address", "1 Example Street");

        when(dataStore.getRowById("Customer", "123")).thenReturn(Optional.of(row));

        Customer customer = repository.findById("123");

        assertNotNull(customer);
        assertEquals("123", customer.getCustomerId());
        assertEquals("123", customer.getRowId());
        customer.setCustomerName("Updated Customer");
        repository.update(customer);

        verify(dataStore).updateRow(eq("Customer"), eq("123"), argThat(fields ->
                "Updated Customer".equals(fields.get("customerName"))));
    }

    @Test
    void findByEmailAndExistsByEmail_MatchesCaseInsensitively() {
        ZCRowObject row = ZCRowObject.getInstance();
        row.set("ROWID", 123L);
        row.set("customerName", "Customer");
        row.set("email", "customer@example.com");
        when(dataStore.getAllRows("Customer")).thenReturn(List.of(row));

        Customer found = repository.findByEmail("Customer@Example.com");
        assertNotNull(found);
        assertEquals("123", found.getCustomerId());

        assertTrue(repository.existsByEmail("CUSTOMER@example.com", null));
        assertFalse(repository.existsByEmail("CUSTOMER@example.com", "123"));
    }

    @Test
    void deleteById_DeletesCatalystRowMatchingRowId() {
        ZCRowObject row = ZCRowObject.getInstance();
        row.set("ROWID", 123L);
        row.set("customerName", "Customer");
        when(dataStore.getRowById("Customer", "123")).thenReturn(Optional.of(row));

        repository.deleteById("123");

        verify(dataStore).deleteRow("Customer", "123");
    }
}
