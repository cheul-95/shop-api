package com.lsch.shop.domain.order.entity;

import com.lsch.shop.domain.category.entity.Category;
import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.product.entity.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
class OrderTest {

    @Test
    void addItem_calculatesTotalPrice() {
        Member member = new Member("test@test.com", "test", "테스터");

        Order order = new Order(member);

        Category category = new Category("전자제품");

        Product noteBook = new Product("노트북", category, 1000000, 100);
        Product mouse = new Product("마우스", category, 30000, 100);

        OrderItem notebookItem = new OrderItem(noteBook, 2);
        OrderItem mouseItem = new OrderItem(mouse, 1);

        order.addItem(notebookItem);
        order.addItem(mouseItem);

        assertThat(order.getTotalPrice()).isEqualTo(2_030_000);
        assertThat(order.getItems()).hasSize(2);
        assertThat(notebookItem.getOrder()).isEqualTo(order);   // 양방향 연결 확인
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
