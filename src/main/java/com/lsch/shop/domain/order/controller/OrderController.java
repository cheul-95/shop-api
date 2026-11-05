package com.lsch.shop.domain.order.controller;

import com.lsch.shop.domain.auth.dto.AuthResponse;
import com.lsch.shop.domain.order.dto.OrderRequest;
import com.lsch.shop.domain.order.dto.OrderResponse;
import com.lsch.shop.domain.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@AuthenticationPrincipal Long memberId, @Valid @RequestBody OrderRequest request) {
        OrderResponse orderResponse = orderService.createOrder(memberId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderResponse);

    }
}