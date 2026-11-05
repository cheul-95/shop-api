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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        List<Long> productIds = request.items().stream()
                .map(item -> item.productId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Product> products = productRepository.findAllById(productIds);

        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItem> orderItems = request.items().stream()
                .map(item -> OrderItem.create(productMap.get(item.productId()), item.quantity())).toList();

        Order order = Order.create(member, orderItems.toArray(OrderItem[]::new));
        orderRepository.save(order);

        return OrderResponse.from(order);
    }
}
