package com.lsch.shop.domain.order.dto;

import com.lsch.shop.domain.order.entity.Order;
import com.lsch.shop.domain.order.entity.OrderStatus;

import java.util.List;

public record OrderResponse(Long id, OrderStatus orderStatus, int totalPrice, List<OrderItemResponse> items) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderStatus(),
                order.getTotalPrice(),
                order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .toList()
        );
    }
}
