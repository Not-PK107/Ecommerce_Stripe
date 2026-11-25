package com.example.ecommerce.service;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.exception.ResourceNotFoundException;
import com.example.ecommerce.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product addProduct(Product product) {
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(String productId) {
        return productRepository.findById(productId);
    }

    public Product getActiveProductOrThrow(String productId) {
        Product product = getProductById(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
        return product;
    }

    public Product updateProduct(String productId, Product updatedProduct) {
        Product existing = getActiveProductOrThrow(productId);
        existing.setProdName(updatedProduct.getProdName());
        existing.setProdDescription(updatedProduct.getProdDescription());
        existing.setProdPrice(updatedProduct.getProdPrice());
        existing.setProdQuantity(updatedProduct.getProdQuantity());
        existing.setProdImage(updatedProduct.getProdImage());
        existing.setProdCategory(updatedProduct.getProdCategory());
        return productRepository.update(existing);
    }

    public synchronized Product deductStock(String productId, int quantity) {
        Product existing = getActiveProductOrThrow(productId);
        int currentQty = existing.getProdQuantity();
        int newQty = Math.max(0, currentQty - quantity);
        existing.setProdQuantity(newQty);
        return productRepository.update(existing);
    }

    public boolean deleteProduct(String productId) {
        return productRepository.deleteById(productId);
    }
}