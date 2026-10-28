package com.lsch.shop.domain.order.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("ORDER_PENDING", "준비중"),
    PAID("ORDER_PAID", "주문완료"),
    CANCELED("ORDER_CANCELED","주문취소");

    private final String key;
    private final String title;
}



