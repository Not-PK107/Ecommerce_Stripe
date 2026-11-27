package com.example.ecommerce;

import com.example.ecommerce.dto.CustomerRequest;
import com.example.ecommerce.dto.CustomerResponse;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Customer;
import com.example.ecommerce.repository.CustomerRepository;
import com.example.ecommerce.service.CustomerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void register_WhenEmailAlreadyExists_RejectsRegistration() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.existsByEmail("person@example.com", null)).thenReturn(true);
        CustomerService service = new CustomerService(repository);

        assertThrows(ConflictException.class, () -> service.register(request()));

        verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void login_WhenCustomerExists_ReturnsCustomerResponse() {
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = Customer.builder()
                .customerId("123")
                .customerName("John Doe")
                .email("john@example.com")
                .phone("1234567890")
                .address("1 Main St")
                .build();
        when(repository.findByEmail("john@example.com")).thenReturn(customer);
        CustomerService service = new CustomerService(repository);

        CustomerResponse response = service.login("John@Example.com");

        assertEquals("123", response.getCustomerId());
        assertEquals("John Doe", response.getCustomerName());
        assertEquals("john@example.com", response.getEmail());
    }

    @Test
    void login_WhenCustomerDoesNotExist_ThrowsResourceNotFoundException() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findByEmail("missing@example.com")).thenReturn(null);
        CustomerService service = new CustomerService(repository);

        assertThrows(ResourceNotFoundException.class, () -> service.login("missing@example.com"));
    }

    @Test
    void register_WhenEmailFormatIsInvalid_ThrowsIllegalArgumentException() {
        CustomerRepository repository = mock(CustomerRepository.class);
        CustomerService service = new CustomerService(repository);

        CustomerRequest request = new CustomerRequest();
        request.setCustomerName("Customer");
        request.setEmail("bad-email");
        request.setPhone("1234567890");
        request.setAddress("1 Example Street");

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void register_WhenNameContainsNumbers_ThrowsIllegalArgumentException() {
        CustomerRepository repository = mock(CustomerRepository.class);
        CustomerService service = new CustomerService(repository);

        CustomerRequest request = request();
        request.setCustomerName("User 123");

        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    @Test
    void register_WhenPhoneContainsLettersOrNotTenDigits_ThrowsIllegalArgumentException() {
        CustomerRepository repository = mock(CustomerRepository.class);
        CustomerService service = new CustomerService(repository);

        CustomerRequest request = request();
        request.setPhone("12345abcde");
        assertThrows(IllegalArgumentException.class, () -> service.register(request));

        request.setPhone("12345");
        assertThrows(IllegalArgumentException.class, () -> service.register(request));

        request.setPhone("1234567890123");
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
    }

    private CustomerRequest request() {
        CustomerRequest request = new CustomerRequest();
        request.setCustomerName("Customer");
        request.setEmail("Person@Example.com");
        request.setPhone("1234567890");
        request.setAddress("1 Example Street");
        return request;
    }
}
