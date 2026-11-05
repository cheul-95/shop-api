# 08. 주문 생성 API (보너스)

## 미션 요구사항

- `POST /api/orders`: 로그인한 회원의 주문 생성 + 재고 차감
- 재고 부족 시 409
- **완료 조건**: DB에서 재고 감소 확인, `@Transactional`을 제거하면 재고가 줄지 않는 것 확인

---

## 1. 구조와 요청 흐름

```
domain/order/
├── controller/OrderController.java     POST /api/orders
├── service/OrderService.java           @Transactional, 주문 생성
├── repository/OrderRepository.java
├── dto/
│   ├── OrderRequest.java               { items: [OrderItemRequest] }
│   ├── OrderItemRequest.java           { productId, quantity }
│   ├── OrderResponse.java              { id, orderStatus, totalPrice, items }
│   └── OrderItemResponse.java          { productId, productName, quantity, orderPrice }
└── entity/                             (미션 2) Order, OrderItem, OrderStatus

domain/category/repository/CategoryRepository.java   테스트 데이터 준비용으로 추가
```

```
POST /api/orders (Bearer 토큰)
 → JwtAuthenticationFilter     : 토큰 → SecurityContext (principal = memberId)
 → anyRequest().authenticated(): 인증 없으면 401
 → OrderController             : @Valid 검증 실패 시 400
 → OrderService.createOrder    : ───────── 트랜잭션 시작 ─────────
      회원 조회        (없으면 404 MEMBER_NOT_FOUND)
      상품마다:
        상품 조회      (없으면 404 PRODUCT_NOT_FOUND)
        OrderItem.create → product.decreaseStock (부족하면 409 OUT_OF_STOCK)
      Order.create(member, items...) → totalPrice 계산
      orderRepository.save(order)   → INSERT orders, order_item (cascade)
      OrderResponse.from(order)
                                 ─── 커밋: flush → dirty checking → UPDATE product ───
 → 201 Created
```

## 2. API

```
POST /api/orders
Authorization: Bearer <accessToken>
Content-Type: application/json

{"items":[{"productId":1,"quantity":2},{"productId":8,"quantity":1}]}
```

```json
{
  "id": 1,
  "orderStatus": "PENDING",
  "totalPrice": 2430000,
  "items": [
    {"productId": 1, "productName": "노트북", "quantity": 2, "orderPrice": 1200000},
    {"productId": 8, "productName": "무선 마우스", "quantity": 1, "orderPrice": 30000}
  ]
}
```

| 상황 | 상태 | code |
|---|---|---|
| 정상 | **201** | - |
| 수량 0 이하, `items` 비어 있음, `productId` 없음 | 400 | `INVALID_INPUT` |
| 토큰 없음 / 유효하지 않음 | 401 | `UNAUTHORIZED` |
| 회원 없음 | 404 | `MEMBER_NOT_FOUND` |
| 상품 없음 | 404 | `PRODUCT_NOT_FOUND` |
| 재고 부족 | **409** | `OUT_OF_STOCK` |

## 3. 결정한 것들

### URL에 동사를 넣지 않는다

- `POST /api/orders/createOrder`가 아니라 `POST /api/orders`로 만들었다.
- REST에서는 **URL로 대상(명사)을 나타내고, 행위는 HTTP 메서드로 표현**한다. "주문 생성"은 `orders` 컬렉션에 POST를 보내는 것만으로 표현된다.
  - 생성 `POST /api/orders`, 조회 `GET /api/orders/{id}`, 취소 `POST /api/orders/{id}/cancel` (또는 `PATCH`)

### 생성은 201

- 객체를 그대로 반환하면 기본값 200이 나간다. 회원가입(`AuthController.signup`)과 같은 방식으로 `ResponseEntity.status(HttpStatus.CREATED).body(...)`를 썼다.

### 재고 부족은 400이 아니라 409

- 미션 2에서는 `OUT_OF_STOCK`을 400으로 정했었다. 이번에 409(CONFLICT)로 바꿨다.
- 요청 형식은 맞지만 **서버의 현재 상태(재고)와 충돌**해서 처리할 수 없는 경우라 409가 더 맞다. 같은 요청이라도 재고가 있을 때는 성공한다.

### 재고 차감을 `OrderItem.create`로 강제

```java
private OrderItem(Product product, int quantity) { ... }        // 외부에서 new 불가

public static OrderItem create(Product product, int quantity) {
    OrderItem orderItem = new OrderItem(product, quantity);
    product.decreaseStock(quantity);
    return orderItem;
}
```

- 처음 서비스를 짤 때 `new OrderItem(product, quantity)`를 쓰려고 생성자를 `public`으로 바꿨다. 그러면 **주문은 만들어지는데 재고는 줄지 않는다.** 컴파일도 되고 예외도 나지 않아서 놓치기 쉽다.
- 생성자를 `private`으로 되돌려서, 주문 항목을 만드는 방법을 재고를 차감하는 `create` 하나로 막았다. 실수를 **컴파일 단계에서** 막는 설계다.

### 수량 검증은 두 군데

- `OrderItemRequest`의 `@Min(1)`: 컨트롤러에서 400 `INVALID_INPUT`
- `Product.decreaseStock`의 `quantity <= 0` 체크: `INVALID_QUANTITY`
- API로 들어오는 요청은 앞에서 걸러진다. 엔티티 쪽 체크는 API가 아닌 경로(다른 서비스, 테스트)에서도 도메인 규칙을 지키기 위한 것이라 남겨뒀다.

### `List` → 가변 인자

```java
Order order = Order.create(member, orderItems.toArray(OrderItem[]::new));
```

- `Order.create(Member, OrderItem...)`는 가변 인자라 배열을 받는다. 스트림으로 만든 `List<OrderItem>`을 `toArray`로 바꿔서 넘겼다.
- `Order.create`에 `List`를 받는 오버로드를 추가하는 방법도 있다.

### `save` 다음에 `from`

- ID 생성 전략이 `IDENTITY`라 `save` 시점에 INSERT가 실행되고 `order.getId()`가 채워진다. 순서를 바꾸면 응답의 `id`가 null이 된다.

### 엔티티 → DTO 변환은 서비스 안에서

- `open-in-view: false`라 트랜잭션이 끝나면 영속성 컨텍스트도 닫힌다. 지연 로딩이 필요한 변환을 컨트롤러에서 하면 `LazyInitializationException`이 날 수 있다.
- 그래서 `OrderResponse.from(order)`를 트랜잭션 안(서비스)에서 호출했다.

## 4. 완료 조건 확인

앱을 실행하고 curl로 확인했다. `data.sql` 기준으로 노트북(1번) 재고 10, 무선 마우스(8번) 재고 100.

| 요청 | 응답 | DB |
|---|---|---|
| 노트북 2 + 마우스 1 | `{"id":1,"orderStatus":"PENDING","totalPrice":2430000,...}` | 노트북 10 → **8**, 마우스 100 → **99**, `orders` 1행, `order_item` 2행 (`order_price` 저장) |
| 노트북 999 | `{"code":"OUT_OF_STOCK",...}` | 변화 없음 |
| 토큰 없음 | `{"code":"UNAUTHORIZED",...}` | - |
| 수량 0 | `{"code":"INVALID_INPUT",...}` | - |

- totalPrice: 1,200,000 × 2 + 30,000 × 1 = 2,430,000
- `order_item.order_price`에 **주문 시점의 가격**이 저장된다. 나중에 상품 가격이 바뀌어도 주문 내역은 그대로다.

## 5. `@Transactional` 제거 실험

`OrderService`의 `@Transactional`을 주석 처리하고 같은 정상 주문을 두 번 보냈다.

| | orders | order_item | 재고 (노트북 / 마우스) |
|---|---|---|---|
| 실험 전 | 1건 | 2행 | 8 / 99 |
| 실험 후 | **3건** | **6행** | **8 / 99** (그대로) |

응답은 정상(`{"id":2,...}`)이었다. **주문은 저장됐는데 재고는 줄지 않았다.** 노트북 4개가 팔렸다고 기록됐지만 재고는 그대로인, 데이터가 서로 맞지 않는 상태가 됐다.

### 왜 이렇게 됐나

`open-in-view: false`라서 `@Transactional`이 없으면 **Repository 메서드 하나마다 트랜잭션과 영속성 컨텍스트가 따로 열렸다 닫힌다.** (`SimpleJpaRepository`의 메서드에는 각각 `@Transactional`이 붙어 있다.)

```
productRepository.findById(1)  → [트랜잭션A: SELECT → 커밋, 컨텍스트 닫힘]
                                    product는 준영속(detached) 상태
product.decreaseStock(2)       → 자바 객체의 값만 바뀜. 지켜보는 영속성 컨텍스트가 없음
orderRepository.save(order)    → [트랜잭션B: INSERT orders, order_item → 커밋]
                                    product는 트랜잭션B가 관리하는 대상이 아님 → UPDATE 없음
```

- **dirty checking은 영속성 컨텍스트가 관리하는(영속 상태) 엔티티에만 동작한다.** 커밋 직전 flush 때 스냅샷과 비교해서 바뀐 필드로 UPDATE를 만든다.
- `@Transactional`이 있으면 메서드 전체가 하나의 트랜잭션, 하나의 영속성 컨텍스트로 묶인다. 서비스 코드에 `update`나 `save(product)`가 없는데도 커밋 때 `UPDATE product`가 나간다.
- JPA는 UPDATE를 직접 쓰지 않고 dirty checking에 맡기기 때문에, 트랜잭션이 없으면 UPDATE가 **실패하는 게 아니라 아예 만들어지지 않는다.**

### `open-in-view: true`였다면

- 영속성 컨텍스트가 요청 끝까지 하나로 유지된다. `findById`가 끝나도 product는 **영속 상태**로 남는다.
- 그 상태에서 `save(order)`의 트랜잭션B가 커밋될 때 flush가 일어난다. 이때 **같은 컨텍스트에 있는 product의 변경도 함께 감지돼 UPDATE가 나간다.** 결과만 보면 재고가 줄어서 정상처럼 보인다.
- 하지만 상관없는 `save` 호출에 변경이 우연히 같이 실려 나간 것이다. `save`가 없는 로직이었다면 변경은 사라진다. 응답을 렌더링한 뒤 OSIV가 컨텍스트를 닫을 때는 **flush하지 않는다.**
- 이렇게 언제 반영되는지 예측하기 어려운 동작 때문에 이 프로젝트는 OSIV를 껐다.
- (직접 실험하지는 않았다. 확인하려면 `open-in-view: true` + `@Transactional` 제거 상태에서 주문하고 SQL 로그를 본다.)

### 롤백되는 예외

- 상품 A는 재고가 충분하고 B가 부족하면, A의 `decreaseStock`은 **메모리에서는** 실행된다. B에서 예외가 나면 트랜잭션이 롤백되고, flush 없이 끝나서 A의 변경도 DB에 반영되지 않는다.
- 롤백이 되는 건 `BusinessException`이 **`RuntimeException`을 상속**하기 때문이다. `@Transactional`은 기본적으로 언체크 예외(`RuntimeException`, `Error`)에서만 롤백한다. 체크 예외(`Exception`)라면 예외가 나도 커밋된다. 바꾸려면 `rollbackFor`를 지정한다.

## 6. 테스트: `OrderServiceTest`

`@SpringBootTest` + Testcontainers 통합 테스트. 서비스 → DB까지 실제로 실행하고, **새로 조회한 값**으로 검증한다.

| 테스트 | 검증 |
|---|---|
| `createOrder_decreasesStock` | 마우스 3개 주문 → totalPrice 90,000, DB 재고 100 → 97 |
| `createOrder_outOfStock_throws` | 재고 100에 101개(경계값) 주문 → `OUT_OF_STOCK` |
| `createOrder_partialOutOfStock_rollsBackAll` | 한 주문에 [노트북 3, 마우스 101] → 예외, 노트북 재고 100 그대로, 주문 0건 |
| `createOrder_productNotFound_throws` | 없는 상품 ID(`Long.MAX_VALUE`), 수량 1 → `PRODUCT_NOT_FOUND` |
| `createOrder_memberNotFound_throws` | 없는 회원 ID(`Long.MAX_VALUE`) → `MEMBER_NOT_FOUND` |

전체 23개 통과. Docker Desktop이 실행 중이어야 한다([00. 초기 세팅](00-project-setup.md)의 Testcontainers).

### 테스트 클래스에 `@Transactional`을 붙이지 않은 이유

- 테스트에 `@Transactional`을 붙이면 끝나고 자동으로 롤백돼서 편하다. 하지만 서비스의 트랜잭션이 **테스트 트랜잭션에 합류**한다(전파 `REQUIRED`). 영속성 컨텍스트도 하나를 같이 쓴다.
- 그러면 서비스가 끝난 뒤 `findById`로 다시 조회해도 DB가 아니라 **1차 캐시에 남은 같은 객체**가 나온다. 롤백 테스트에서 메모리에서만 줄어든 재고(97)를 보게 되어 결과를 믿을 수 없다. 커밋도 일어나지 않아서 "실제로 DB에 반영됐는지"를 확인할 수 없다.
- 대신 `@BeforeEach`에서 데이터를 만들고, `@AfterEach`에서 **FK 자식 테이블부터** 지운다.

```java
@AfterEach
void tearDown() {
    orderRepository.deleteAll();     // order_item은 cascade로 같이 삭제
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    memberRepository.deleteAll();
}
```

```
order_item ──▶ orders ──▶ member
     │
     └──────▶ product ──▶ category        (화살표 = 참조. 화살표가 시작되는 쪽부터 지운다)
```

### 데이터는 `save`의 반환값에서 ID를 받는다

- `create-drop`이라 테스트 DB는 빈 상태이고, `data.sql`은 `local` 프로필에서만 실행된다. 회원·카테고리·상품을 직접 저장한다.
- `Product → Category`에는 cascade가 없어서 카테고리를 먼저 저장해야 한다. 안 하면 `TransientPropertyValueException`.
- `deleteAll()`은 AUTO_INCREMENT를 초기화하지 않는다. ID를 `1L`처럼 고정하지 말고 `save` 결과의 `getId()`를 필드에 담아 쓴다. 없는 데이터는 생길 수 없는 값(`Long.MAX_VALUE`)을 쓴다. 처음에 `10L`을 썼다가 실행 순서에 따라 깨질 수 있는 걸 발견해서 고쳤다. [트러블슈팅 5](troubleshooting.md) 참고.

### 롤백 테스트는 `@Transactional` 없이도 통과한다

`@Transactional`을 일부러 지우고 `createOrder_partialOutOfStock_rollsBackAll`을 실행했더니 **통과했다.**

```
findById(노트북)         → [트랜잭션A] → 준영속
노트북.decreaseStock(3)  → 메모리만 97. UPDATE는 영원히 안 나감
findById(마우스)         → [트랜잭션B]
마우스.decreaseStock(101)→ 예외
orderRepository.save()   → 실행되지 않음
```

- 지금 구조에서는 **예외가 DB 쓰기보다 먼저 일어난다.** UPDATE는 커밋 때, INSERT는 `save` 때 나가는데 둘 다 예외 뒤에 있다. `@Transactional`이 있어도 롤백이 되돌릴 게 없다.
- 즉 이 테스트가 통과하는 건 트랜잭션 덕분이 아니라 코드 순서 덕분이다.
- 그래도 이 테스트는 **"재고 부족이면 데이터가 하나도 바뀌지 않는다"는 결과를 지키는 회귀 테스트**로 의미가 있다. 주문을 먼저 저장하도록 바꾸거나, `save(product)`를 명시적으로 호출하거나, 중간에 JPQL 조회가 들어가서 flush가 미리 일어나면 예외 전에 DB 쓰기가 생긴다. 그때 `@Transactional`이 없으면 이 테스트가 실패한다.
- `@Transactional` 자체를 검증하는 건 정상 주문 쪽이다. curl 실험에서 "주문은 저장되고 재고는 그대로"로 깨지는 걸 확인했다. 테스트로도 확인했다. `@Transactional`을 지우고 `createOrder_decreasesStock`을 돌리면 **실패**하고, 되돌리면 다시 통과한다. 즉 `@Transactional`을 지키는 건 정상 주문 테스트다.

**배운 점**: 테스트를 만든 뒤 **일부러 코드를 망가뜨려서 테스트가 실패하는지** 확인한다. 망가뜨려도 통과하면 그 테스트가 무엇을 지키는지 다시 생각해 본다.

## 7. 정리

- [x] `POST /api/orders` → 201, 재고 차감, totalPrice 계산
- [x] 재고 부족 409, 토큰 없음 401, 검증 실패 400
- [x] DB에서 재고 감소 확인
- [x] `@Transactional` 제거 시 재고가 줄지 않는 것 확인 (주문만 저장되는 데이터 불일치)
- [x] `OrderServiceTest` 5개 + 전체 23개 통과
- [x] 없는 ID `10L` → `Long.MAX_VALUE`로 바꿔 flaky 테스트 제거

## 8. 남은 것

- **재고 동시성**: 같은 상품에 동시에 주문이 들어오면 두 트랜잭션이 같은 재고를 읽고 각자 차감해서 재고가 덜 줄 수 있다(Lost Update). `ExecutorService`로 동시 주문을 재현한 뒤 비관적 락(`@Lock(PESSIMISTIC_WRITE)`) 또는 낙관적 락(`@Version`)으로 해결한다.
- 상품 개수만큼 `findById`가 실행된다(상품 3개면 SELECT 3번). `findAllById`로 한 번에 조회하도록 개선할 수 있다.
- 주문 취소 API (`Order.cancel()`은 미션 2에서 이미 구현), 내 주문 목록 조회 (fetch join으로 N+1 방지)
- 컨트롤러 테스트(MockMvc): 401 / 400 / 201 응답
