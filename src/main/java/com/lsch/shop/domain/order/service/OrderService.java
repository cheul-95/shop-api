package com.lsch.shop.domain.order.service;

import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.member.repository.MemberRepository;
import com.lsch.shop.domain.order.dto.OrderRequest;
import com.lsch.shop.domain.order.dto.OrderResponse;
import com.lsch.shop.domain.order.entity.Order;
import com.lsch.shop.domain.order.entity.OrderItem;
import com.lsch.shop.domain.order.repository.OrderRepository;
import com.lsch.shop.domain.product.entity.Product;
import com.lsch.shop.domain.product.repository.ProductRepository;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;

    public OrderResponse createOrder(Long memberId, OrderRequest request) {

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        List<OrderItem> orderItems = request.items().stream()
                .map(itemRequest -> {
                    Product product = productRepository.findById(itemRequest.productId()).orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
                    return OrderItem.create(product, itemRequest.quantity());
                }).toList();
        Order order = Order.create(member, orderItems.toArray(OrderItem[]::new));
        orderRepository.save(order);

        return OrderResponse.from(order);
    }
}
