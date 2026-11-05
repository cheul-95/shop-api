package com.lsch.shop.domain.order.dto;

import com.lsch.shop.domain.order.entity.OrderItem;

public record OrderItemResponse(Long productId, String productName, int quantity, int orderPrice) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getOrderPrice()
        );
    }
}