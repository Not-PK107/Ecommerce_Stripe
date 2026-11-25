package com.example.ecommerce.service;

import com.example.ecommerce.dto.CustomerRequest;
import com.example.ecommerce.dto.CustomerResponse;
import com.example.ecommerce.exception.ConflictException;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.model.Customer;
import com.example.ecommerce.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public synchronized CustomerResponse register(CustomerRequest request) {

        validateCustomerFields(request);
        String normalizedEmail = normalizeEmail(request.getEmail());
        ensureEmailAvailable(normalizedEmail, null);

        Customer customer = Customer.builder()
                .customerName(request.getCustomerName().trim())
                .email(normalizedEmail)
                .phone(request.getPhone().trim())
                .address(request.getAddress().trim())
                .createdAt(LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();

        return toResponse(customerRepository.insert(customer));
    }


    public CustomerResponse getById(String customerId) {
        return toResponse(getCustomerOrThrow(customerId));
    }

    public CustomerResponse login(String email) {
        String normalizedEmail = normalizeEmail(email);
        Customer customer = customerRepository.findByEmail(normalizedEmail);
        if (customer == null) {
            throw new ResourceNotFoundException("No account found with email: " + email);
        }
        return toResponse(customer);
    }

    public Customer getCustomerOrThrow(String customerId) {
        Customer customer = customerRepository.findById(customerId);
        if (customer == null) {
            throw new ResourceNotFoundException("Customer not found with id: " + customerId);
        }
        return customer;
    }

    public List<CustomerResponse> getAll() {
        return customerRepository.findAll().stream().map(this::toResponse).toList();
    }

    public synchronized CustomerResponse update(String customerId, CustomerRequest request) {
        Customer customer = getCustomerOrThrow(customerId);
        validateCustomerFields(request);
        String normalizedEmail = normalizeEmail(request.getEmail());
        ensureEmailAvailable(normalizedEmail, customerId);

        customer.setCustomerName(request.getCustomerName().trim());
        customer.setEmail(normalizedEmail);
        customer.setPhone(request.getPhone().trim());
        customer.setAddress(request.getAddress().trim());
        return toResponse(customerRepository.update(customer));
    }

    public void delete(String customerId) {
        getCustomerOrThrow(customerId);
        customerRepository.deleteById(customerId);
    }

    public synchronized CustomerResponse findOrCreateForZohoUser(String email, String name) {
        String normalizedEmail = normalizeEmail(email);
        Customer existing = customerRepository.findByEmail(normalizedEmail);
        if (existing != null) {
            return toResponse(existing);
        }

        // Clean customer name to conform to name validation (no numbers, letters and basic punctuation only)
        String cleanName = name != null ? name.replaceAll("[0-9]", "").trim() : "";
        if (cleanName.isBlank() && normalizedEmail.contains("@")) {
            cleanName = normalizedEmail.substring(0, normalizedEmail.indexOf('@')).replaceAll("[0-9]", "").trim();
        }
        if (cleanName.isBlank() || !NAME_PATTERN.matcher(cleanName).matches()) {
            cleanName = "Zoho Customer";
        }

        Customer newCustomer = Customer.builder()
                .customerName(cleanName)
                .email(normalizedEmail)
                .phone("9999999999")
                .address("Please provide delivery address")
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();

        return toResponse(customerRepository.insert(newCustomer));
    }

    private static final java.util.regex.Pattern NAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-zA-Z\\s.'-]+$");
    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^\\d{10}$");
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
    );

    private void validateCustomerFields(CustomerRequest request) {
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (!NAME_PATTERN.matcher(request.getCustomerName().trim()).matches()) {
            throw new IllegalArgumentException("Name must not contain numbers and can only contain letters");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (!PHONE_PATTERN.matcher(request.getPhone().trim()).matches()) {
            throw new IllegalArgumentException("Phone number must contain only numbers and be 10 digits");
        }
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            throw new IllegalArgumentException("Delivery address is required");
        }
    }

    private void ensureEmailAvailable(String email, String excludedCustomerId) {
        if (customerRepository.existsByEmail(email, excludedCustomerId)) {
            throw new ConflictException("A customer with this email already exists");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Invalid email format. Please provide a valid email address (e.g. name@example.com).");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private CustomerResponse toResponse(Customer customer) {
        return CustomerResponse.builder()
                .customerId(customer.getCustomerId())
                .customerName(customer.getCustomerName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}