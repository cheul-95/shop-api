# 02. 엔티티 설계

> 커밋
> - `73459c9 feat: 미션2 엔티티 설계 (Member, Category, Product) 및 재고 차감 로직`
> - `9ff154f feat: 미션2 Order, OrderItem 엔티티 및 주문 금액 계산`

## 미션 요구사항

| 엔티티 | 요구사항 |
|---|---|
| Member | email unique, password, name, role(USER/ADMIN, 문자열로 저장) |
| Category | name |
| Product | category(N:1), name, price, stock. `decreaseStock()`: 0 이하거나 재고 부족이면 예외 |
| Order | member(N:1), status(PENDING/PAID/CANCELED), totalPrice, items(1:N 양방향, 함께 저장/삭제). 테이블 이름 주의 |
| OrderItem | order, product, 주문 시점 가격, quantity. **생성할 때 재고 차감** |

**체크리스트**: protected 기본 생성자, 정적 팩토리 `create()`, setter 없음, 모든 `@ManyToOne`에 LAZY, 연관관계 편의 메서드, `Order.cancel()` 시 재고 복구

**완료 조건**: 앱을 실행하면 SQL 로그에 테이블 5개의 CREATE 문이 보이고, `ProductTest`(재고 차감 / 재고 부족)가 통과한다.

진행 상황은 [8. 미션 2 남은 과제](#8-미션-2-남은-과제) 참고.

## ERD

```
member 1 ──── N orders 1 ──── N order_item N ──── 1 product N ──── 1 category
```

- `Order`와 `Product`는 원래 N:M 관계(주문 하나에 여러 상품, 상품 하나가 여러 주문에)다.
- 중간에 `OrderItem`을 둬서 1:N, N:1 두 개로 풀었다. 수량, 주문 당시 가격처럼 **관계 자체에 붙는 정보**를 저장할 곳이 필요하기 때문이다.

## 1. 엔티티 기본 틀

모든 엔티티가 같은 형태를 따른다.

```java
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;
    ...
}
```

| 요소 | 이유 |
|---|---|
| `@Entity` | 없으면 JPA가 엔티티로 인식하지 않는다. 테이블도 안 생기고, 다른 엔티티에서 참조하면 실행 시 오류 |
| `@Table(name = ...)` | 테이블명을 명시 |
| `@GeneratedValue(IDENTITY)` | MySQL `AUTO_INCREMENT` 사용 |
| `@Getter`만, `@Setter`는 없음 | 아무 데서나 값을 바꾸지 못하게. 변경은 의미 있는 메서드로만 (`decreaseStock()`, `updatePassword()`) |
| `@NoArgsConstructor(PROTECTED)` | JPA는 기본 생성자가 필요하다(조회 결과로 객체를 만들 때, 프록시 생성할 때). `protected`로 막아서 외부에서 빈 객체를 `new`로 만들지 못하게 |
| `extends BaseTimeEntity` | 생성일/수정일 자동 기록 |

## 2. 이름 짓기 규칙

| 대상 | 규칙 | 예 |
|---|---|---|
| 자바 클래스 | PascalCase, **단수형** | `OrderItem` (`OrderItems` X) |
| 자바 필드 | camelCase | `totalPrice` (`total_Price` X) |
| DB 테이블/컬럼 | snake_case | `order_item`, `total_price`, `order_item_id` |
| PK 컬럼 | `테이블명_id` | `member_id`, `product_id` |
| 컬렉션 필드 | 복수형 | `items` |

- 자바 필드명에 `_`를 쓰면 Lombok이 `getTotal_Price()` 같은 어색한 getter를 만든다.
- Spring Boot의 기본 네이밍 전략(`CamelCaseToUnderscoresNamingStrategy`)이 camelCase 컬럼명을 snake_case로 바꿔주긴 한다. 그래도 코드에 적힌 이름과 실제 컬럼명이 다르면 헷갈리므로 처음부터 snake_case로 쓴다.
- enum 이름도 구체적으로 짓는다. `Status`는 나중에 결제 상태, 배송 상태와 겹치므로 `OrderStatus`로.

### 예약어 주의: `order` → `orders`

`ORDER`는 SQL 예약어(`ORDER BY`)라서 `create table order (...)`가 문법 오류로 실패한다.
테이블명은 `orders`로 지었다. 같은 이유로 `user`, `group` 같은 이름도 피한다.

## 3. Member

```java
@Column(name = "email", unique = true, nullable = false, length = 100)
private String email;

@Enumerated(EnumType.STRING)
@Column(nullable = false)
private Role role;

public Member(String email, String encodedPassword, String name) {
    this.email = email;
    this.password = encodedPassword;
    this.name = name;
    this.role = Role.USER;
}
```

- **생성자에서 `role`을 `USER`로 고정**: 가입하자마자 관리자가 될 수 없게. 관리자 지정은 `changeRole()`로 따로.
- 파라미터 이름을 `encodedPassword`로 지어서, 평문이 아니라 암호화된 값을 넘겨야 한다는 걸 드러낸다.

### @Enumerated(EnumType.STRING)

| 방식 | DB에 저장되는 값 | 문제 |
|---|---|---|
| `ORDINAL` (기본값) | `0`, `1` (순서) | enum 중간에 값을 추가하면 기존 데이터의 의미가 바뀐다 |
| `STRING` | `"USER"`, `"ADMIN"` | 없음. **항상 STRING을 쓴다** |

## 4. Product와 재고 차감

```java
public void decreaseStock(int quantity) {
    if (quantity <= 0) {
        throw new BusinessException(ErrorCode.INVALID_QUANTITY);
    }
    if (this.stock - quantity < 0) {
        throw new BusinessException(ErrorCode.OUT_OF_STOCK);
    }
    this.stock -= quantity;
}
```

- 재고를 바꾸는 로직을 **엔티티 안에** 뒀다. 서비스에서 `product.setStock(product.getStock() - 3)`처럼 하면 검증을 빼먹을 수 있다.
- 검증 → 실패하면 예외, 통과하면 변경. 재고가 음수가 되는 상태는 절대 만들어지지 않는다.
- 이렇게 데이터와 그 데이터를 다루는 규칙을 한 곳에 두는 것을 **풍부한 도메인 모델**이라고 한다.

## 5. 다대일(N:1) 단방향: `@ManyToOne`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id", nullable = false)
private Category category;
```

- DB의 FK(`product.category_id`)를 객체 참조로 표현한 것.
- `@JoinColumn(name = ...)`: FK 컬럼 이름.

### 항상 `fetch = FetchType.LAZY`

| | EAGER (`@ManyToOne` 기본값) | LAZY |
|---|---|---|
| 조회 시점 | Product를 조회하면 Category도 **즉시** 조회 | Category에 실제로 접근할 때 조회 |
| 문제 | 필요 없는 조인, 목록 조회 시 N+1 | 트랜잭션 밖에서 접근하면 `LazyInitializationException` |

- `@ManyToOne`, `@OneToOne`은 기본값이 EAGER라서 **직접 LAZY로 바꿔줘야 한다.**
- 연관 데이터가 필요하면 fetch join 등으로 그때그때 함께 조회한다.

## 6. Order와 OrderItem: 일대다(1:N) 양방향

### 최종 코드

```java
public class Order extends BaseTimeEntity {
    ...
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    private OrderStatus orderStatus;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(Member member) {
        this.member = member;
        this.orderStatus = OrderStatus.PENDING;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        totalPrice += item.getOrderPrice() * item.getQuantity();
    }
}
```

```java
public class OrderItem {
    ...
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "order_price", nullable = false)
    private int orderPrice;

    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.orderPrice = product.getPrice();
    }

    void setOrder(Order order) {
        this.order = order;
    }
}
```

### 연관관계의 주인

- DB에서 FK(`order_id`)는 `order_item` 테이블에 있다.
- JPA에서는 **FK를 가진 쪽이 연관관계의 주인**이다 → `OrderItem.order`가 주인.
- 주인만 FK 값을 INSERT/UPDATE 할 수 있다. 반대쪽(`Order.items`)은 **읽기 전용**.

| 쪽 | 어노테이션 | 의미 |
|---|---|---|
| `OrderItem.order` (주인) | `@ManyToOne` + `@JoinColumn(name = "order_id")` | FK를 관리 |
| `Order.items` (주인 아님) | `@OneToMany(mappedBy = "order")` | "OrderItem의 `order` 필드에 매핑돼 있다" |

- `mappedBy = "order"`의 `"order"`는 테이블명이나 컬럼명이 아니라 **상대 클래스의 자바 필드명**이다.

### cascade와 orphanRemoval: "함께 저장/삭제"

| 옵션 | 동작 |
|---|---|
| `cascade = CascadeType.ALL` | `Order`를 저장/삭제하면 `items`도 같이 저장/삭제 |
| `orphanRemoval = true` | `items.remove(item)`처럼 리스트에서 빠진 항목은 DB에서도 삭제 |

```java
Order order = new Order(member);
order.addItem(new OrderItem(notebook, 2));
order.addItem(new OrderItem(mouse, 1));

orderRepository.save(order);   // cascade 덕분에 OrderItem 2개도 같이 INSERT
```

- `OrderItem`은 `Order` 없이는 의미가 없다 (생명주기가 같다). 이럴 때 cascade를 쓴다.
- 반대로 `Product`처럼 다른 곳에서도 쓰는 엔티티에는 cascade를 걸면 안 된다. 주문을 지우면 상품까지 지워진다.

### 컬렉션은 바로 초기화

```java
private List<OrderItem> items = new ArrayList<>();
```

초기화하지 않으면 `items`가 `null`이라 `add()`에서 NPE가 난다.

### 연관관계 편의 메서드 `addItem()`

```java
order.getItems().add(item);   // 이것만 하면 item.order는 null
```

- 양방향이면 **양쪽 다** 값을 세팅해야 한다.
- DB에는 주인(`OrderItem.order`)의 값만 반영되므로, 한쪽만 세팅하면 FK가 비어서 저장된다(`nullable = false`라 오류).
- 두 줄을 항상 같이 실행하도록 메서드 하나로 묶은 것이 편의 메서드다.

### `setOrder()`를 package-private으로

- 접근제어자를 안 붙이면 **같은 패키지에서만** 호출할 수 있다.
- `public` setter를 열면 아무 데서나 주문을 바꿔치기할 수 있다.
- 같은 패키지(`order.entity`)에 있는 `Order.addItem()`만 호출할 수 있게 막았다.

### 주문 당시 가격 `orderPrice`를 따로 저장하는 이유

- `product.getPrice()`를 그대로 참조하면, 나중에 상품 가격이 바뀌었을 때 **과거 주문 금액까지 바뀐 것처럼 보인다.**
- 그래서 주문 시점의 가격을 `OrderItem`에 복사해서 기록한다. 생성자에서 `this.orderPrice = product.getPrice()`로 세팅하고, 외부에서 받지 않는다.
- 금액 계산에도 `product.getPrice()`가 아니라 `orderPrice`를 쓴다.
  - 할인이 생기면 `orderPrice`에 할인가가 들어가므로 총액에 자연스럽게 반영된다.
  - `product`는 LAZY라서 DB에서 불러온 `OrderItem`에 `getProduct().getPrice()`를 호출하면 상품 SELECT 쿼리가 추가로 나갈 수 있다.

### totalPrice는 외부에서 받지 않고 계산

- 생성자에서 `totalPrice`를 받으면 실제 상품 목록과 금액이 어긋날 수 있다.
- `addItem()`이 아이템을 추가할 때마다 `orderPrice × quantity`를 누적한다 → 목록과 총액이 항상 일치한다.

### 생성자에서 상태 고정, 쓰지 않는 파라미터는 받지 않기

```java
// X: orderStatus를 받지만 무시함
public Order(Member member, OrderStatus orderStatus, int totalPrice) {
    this.orderStatus = OrderStatus.PENDING;
}

// O
public Order(Member member) {
    this.member = member;
    this.orderStatus = OrderStatus.PENDING;
}
```

- 새 주문은 항상 `PENDING`으로 시작해야 한다. 생성 시점에 `PAID`로 만들어지면 안 된다.
- 파라미터로 받아놓고 무시하면, 호출하는 쪽은 `PAID`를 넘기면 그게 들어갈 거라고 오해한다.

## 7. 엔티티 설계 체크리스트

- [ ] `@Entity`, `@Table`, `@Getter`, `@NoArgsConstructor(access = PROTECTED)`
- [ ] `@Setter` 없이, 값 변경은 의미 있는 메서드로
- [ ] 테이블명이 SQL 예약어가 아닌지
- [ ] 자바 필드는 camelCase, 컬럼명은 snake_case
- [ ] 가격, 수량은 숫자 타입 (`String` X)
- [ ] enum은 `@Enumerated(EnumType.STRING)`
- [ ] `@ManyToOne`은 `fetch = LAZY`
- [ ] 양방향이면 `mappedBy` + 편의 메서드 + 컬렉션 초기화
- [ ] 생성자에서 초기 상태 고정, 쓰지 않는 파라미터 없음
- [ ] 쓰지 않는 import 정리

## 8. 미션 2 남은 과제

완료 조건(테이블 5개 생성, `ProductTest` 통과)은 충족했다. 미션 체크리스트 기준으로는 아래가 남았다.

| 항목 | 상태 | 비고 |
|---|---|---|
| protected 기본 생성자 | ✅ | 모든 엔티티 |
| setter 없음 | ✅ | `OrderItem.setOrder()`만 package-private으로 예외 |
| 모든 `@ManyToOne`에 LAZY | ✅ | Product.category, Order.member, OrderItem.order/product |
| 연관관계 편의 메서드 | ✅ | `Order.addItem()` |
| 테이블 5개 CREATE | ✅ | member, category, product, orders, order_item |
| `ProductTest` (재고 차감 / 재고 부족) | ✅ | 수량 0, 음수까지 4개 |
| 정적 팩토리 `create()` | ⬜ | 지금은 `public` 생성자 사용 |
| OrderItem 생성할 때 재고 차감 | ⬜ | 지금은 가격만 기록하고 재고는 그대로 |
| `Order.cancel()` 시 재고 복구 | ⬜ | `cancel()` 메서드 없음 |

### 키워드

- **정적 팩토리 메서드**: `new Order(member)` 대신 `Order.create(member, items...)`. 이름으로 의도를 드러내고, 생성 규칙(초기 상태, 아이템 추가, 총액 계산)을 한 곳에 모을 수 있다. 생성자는 `private`/`protected`로 막는다. (`ErrorResponse.of()`가 같은 패턴)
- **생성 시 재고 차감**: `OrderItem`을 만들 때 `product.decreaseStock(quantity)`를 호출. 재고가 부족하면 `OrderItem`이 만들어지지 않는다.
- **취소 시 재고 복구**: `Product`에 재고를 늘리는 메서드, `OrderItem`에 취소 메서드, `Order.cancel()`에서 상태 변경 + 모든 아이템 취소. 이미 취소된 주문이나 결제 완료된 주문을 취소할 수 있는지도 생각해볼 것.
