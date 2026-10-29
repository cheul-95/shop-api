package com.lsch.shop.domain.product.dto;

import com.lsch.shop.domain.product.entity.Product;

public record ProductResponse(Long id, String name, String categoryName, int price, int stock) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getStock()
                );
    }
}
