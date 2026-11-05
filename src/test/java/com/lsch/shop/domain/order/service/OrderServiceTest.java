package com.lsch.shop.domain.order.service;

import com.lsch.shop.TestcontainersConfiguration;
import com.lsch.shop.domain.category.entity.Category;
import com.lsch.shop.domain.category.repository.CategoryRepository;
import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.member.repository.MemberRepository;
import com.lsch.shop.domain.order.dto.OrderItemRequest;
import com.lsch.shop.domain.order.dto.OrderRequest;
import com.lsch.shop.domain.order.dto.OrderResponse;
import com.lsch.shop.domain.order.repository.OrderRepository;
import com.lsch.shop.domain.product.entity.Product;
import com.lsch.shop.domain.product.repository.ProductRepository;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderServiceTest {

    @Autowired
    ProductRepository productRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    OrderService orderService;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    OrderRepository orderRepository;

    private Long memberId;
    private Long notebookId;   // 재고 넉넉한 상품
    private Long mouseId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(new Member("test@test.com", "test", "테스터"));
        memberId = member.getId();
        Category category = categoryRepository.save(new Category("전자제품"));
        Product notebook = productRepository.save(new Product("노트북", category, 1000000, 100));
        Product mouse = productRepository.save(new Product("마우스", category, 30000, 100));
        notebookId = notebook.getId();
        mouseId = mouse.getId();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void createOrder_decreasesStock() {
        OrderRequest orderRequest = new OrderRequest(List.of(new OrderItemRequest(mouseId, 3)));
        OrderResponse orderResponse = orderService.createOrder(memberId, orderRequest);
        assertThat(orderResponse.totalPrice()).isEqualTo(90000);
        Product product = productRepository.findById(mouseId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(97);
    }

    @Test
    void createOrder_outOfStock_throws() {
        OrderRequest orderRequest = new OrderRequest(List.of(new OrderItemRequest(mouseId, 101)));
        assertThatThrownBy(() -> orderService.createOrder(memberId, orderRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
    }

    @Test
    void createOrder_partialOutOfStock_rollsBackAll() {
        OrderRequest orderRequest = new OrderRequest(List.of(new OrderItemRequest(notebookId, 3), new OrderItemRequest(mouseId, 101)));
        assertThatThrownBy(() -> orderService.createOrder(memberId, orderRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
        Product mouseProduct = productRepository.findById(mouseId).orElseThrow();
        Product notebookProduct = productRepository.findById(notebookId).orElseThrow();
        assertThat(mouseProduct.getStock()).isEqualTo(100);
        assertThat(notebookProduct.getStock()).isEqualTo(100);
        assertThat(orderRepository.count()).isEqualTo(0);
    }

    @Test
    void createOrder_productNotFound_throws() {
        OrderRequest orderRequest = new OrderRequest(List.of(new OrderItemRequest( Long.MAX_VALUE, 1)));
        assertThatThrownBy(() -> orderService.createOrder(memberId, orderRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }
    @Test
    void createOrder_memberNotFound_throws() {
        OrderRequest orderRequest = new OrderRequest(List.of(new OrderItemRequest(mouseId, 98)));
        assertThatThrownBy(() -> orderService.createOrder(Long.MAX_VALUE, orderRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    void createOrder_sameProductTwice_decreasesStockBySum() {
        OrderRequest orderRequest = new OrderRequest(List.of(new OrderItemRequest(mouseId, 2),new OrderItemRequest(mouseId, 3)));
        orderService.createOrder(memberId, orderRequest);
        Product product = productRepository.findById(mouseId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(95);
    }
}