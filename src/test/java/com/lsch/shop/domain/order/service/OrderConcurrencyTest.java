package com.lsch.shop.domain.order.service;

import com.lsch.shop.TestcontainersConfiguration;
import com.lsch.shop.domain.category.entity.Category;
import com.lsch.shop.domain.category.repository.CategoryRepository;
import com.lsch.shop.domain.member.entity.Member;
import com.lsch.shop.domain.member.repository.MemberRepository;
import com.lsch.shop.domain.order.dto.OrderItemRequest;
import com.lsch.shop.domain.order.dto.OrderRequest;
import com.lsch.shop.domain.order.repository.OrderRepository;
import com.lsch.shop.domain.product.entity.Product;
import com.lsch.shop.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrderConcurrencyTest {

    private static final int STOCK = 100;
    private static final int THREAD_COUNT = 100;

    @Autowired
    OrderService orderService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    OrderRepository orderRepository;

    private Long memberId;
    private Long productId;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(new Member("concurrency@test.com", "test", "테스터"));
        memberId = member.getId();
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = productRepository.save(new Product("마우스", category, 30000, STOCK));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void createOrder_concurrently_decreasesStockExactly() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        Map<String, Integer> errors = new ConcurrentHashMap<>();

        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest(productId, 1)));

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    orderService.createOrder(memberId, request);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                    errors.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        int stock = productRepository.findById(productId).orElseThrow().getStock();
        long orderCount = orderRepository.count();
        System.out.printf("성공=%d, 실패=%d, 주문 수=%d, 남은 재고=%d, 실패 원인=%s%n", success.get(), fail.get(), orderCount, stock, errors);

        assertThat(stock).isEqualTo(STOCK - success.get());
    }
}
