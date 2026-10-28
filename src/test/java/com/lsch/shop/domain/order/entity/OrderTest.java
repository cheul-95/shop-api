package com.lsch.shop.domain.order.entity;

import com.lsch.shop.domain.category.entity.Category;
import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.product.entity.Product;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void create_calculatesTotalPrice() {
        Member member = new Member("test@test.com", "test", "테스터");

        Category category = new Category("전자제품");

        Product noteBook = new Product("노트북", category, 1000000, 100);
        Product mouse = new Product("마우스", category, 30000, 100);

        OrderItem notebookItem = OrderItem.create(noteBook, 2);
        OrderItem mouseItem = OrderItem.create(mouse, 1);

        Order order = Order.create(member, notebookItem, mouseItem);

        assertThat(order.getTotalPrice()).isEqualTo(2_030_000);
        assertThat(order.getItems()).hasSize(2);
        assertThat(notebookItem.getOrder()).isEqualTo(order);   // 양방향 연결 확인
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(noteBook.getStock()).isEqualTo(98);


    }
    @Test
    void cancel_restoresStock() {

        Member member = new Member("test@test.com", "test", "테스터");

        Category category = new Category("전자제품");

        Product noteBook = new Product("노트북", category, 1000000, 100);
        Product mouse = new Product("마우스", category, 30000, 100);

        OrderItem notebookItem = OrderItem.create(noteBook, 2);
        OrderItem mouseItem = OrderItem.create(mouse, 1);

        Order order = Order.create(member, notebookItem, mouseItem);
        order.cancel();
        assertThat(noteBook.getStock()).isEqualTo(100);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void cancel_twice_throwsException() {

        Member member = new Member("test@test.com", "test", "테스터");

        Category category = new Category("전자제품");

        Product noteBook = new Product("노트북", category, 1000000, 100);
        Product mouse = new Product("마우스", category, 30000, 100);

        OrderItem notebookItem = OrderItem.create(noteBook, 2);
        OrderItem mouseItem = OrderItem.create(mouse, 1);

        Order order = Order.create(member, notebookItem, mouseItem);
        order.cancel();
        assertThatThrownBy(() -> order.cancel())          // 두 번째 취소: 예외가 나야 정상
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_CANCELED_ORDER);
        assertThat(noteBook.getStock()).isEqualTo(100);
    }
}
