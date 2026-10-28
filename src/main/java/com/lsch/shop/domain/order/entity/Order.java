package com.lsch.shop.domain.order.entity;

import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.global.entity.BaseTimeEntity;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    private Order(Member member) {
        this.member = member;
        this.orderStatus = OrderStatus.PENDING;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        totalPrice += item.getOrderPrice() * item.getQuantity();
    }

    public static Order create(Member member, OrderItem... items) {
        Order order = new Order(member);
        for (OrderItem orderItem : items) {
            order.addItem(orderItem);
        }
        return order;
    }

    public void cancel() {
        //주문이 취소상태가 아닐경우
        if (this.orderStatus == OrderStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED_ORDER);
        }
        //제품 재고 복구
        for (OrderItem orderItem : items) {
            orderItem.cancel();
        }
        //상태변경
        this.orderStatus = OrderStatus.CANCELED;
    }
}
