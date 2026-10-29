# 트러블슈팅

실제로 겪은 문제와 원인, 해결 방법.

---

## 1. 예외 검증 테스트가 실패함

**상황**
`ProductTest`에서 재고 부족, 잘못된 수량 테스트 3개가 실패했다. (처음에는 전부 통과한 줄 알았다)

```
ProductTest > invalidStock() FAILED
    com.lsch.shop.global.error.exception.BusinessException at ProductTest.java:38
4 tests completed, 3 failed
```

**원인**
`decreaseStock()`이 예외를 던지는 건 의도한 동작이었다. 그런데 테스트에서 그 예외를 "기대한 결과"라고 적지 않아서, JUnit이 잡히지 않은 예외로 보고 실패 처리했다.

**해결**
`assertThatThrownBy`로 예외 타입과 `errorCode`를 검증했다.

```java
assertThatThrownBy(() -> product.decreaseStock(10))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
```

**배운 점**
- "예외가 나야 정상"인 테스트는 예외를 기대한다고 직접 적어야 한다.
- 테스트 결과는 실패 개수까지 꼭 확인한다. Gradle이 이전 결과를 재사용(`UP-TO-DATE`)하면 실제로 실행되지 않았을 수도 있다.

---

## 2. 생성자 파라미터를 받아놓고 무시함

**상황**
```java
public Order(Member member, OrderStatus orderStatus, int total_Price) {
    this.orderStatus = OrderStatus.PENDING;   // orderStatus 파라미터 무시
    ...
}

public OrderItem(Product product, int quantity, int orderPrice) {
    this.orderPrice = product.getPrice();     // orderPrice 파라미터 무시
}
```

**원인**
값을 내부에서 정하도록 바꾸면서 파라미터를 지우지 않았다.

**해결**
`Order(Member member)`, `OrderItem(Product product, int quantity)`로 정리했다.

**배운 점**
쓰지 않는 파라미터가 있으면 호출하는 쪽은 그 값이 반영될 거라고 오해한다.

---

## 3. `@EntityGraph`를 붙이면서 `@JoinColumn`까지 바꿈

**상황**
N+1을 없애려고 `@EntityGraph(attributePaths = "category")`를 붙이면서 `Product`의 FK 컬럼 이름도 같이 바꿨다.

```diff
- @JoinColumn(name = "category_id", nullable = false)
+ @JoinColumn(name = "category", nullable = false)
```

**원인**
`attributePaths`에는 `category_id`가 아니라 `category`를 넣는다는 설명을 보고, `@JoinColumn`도 같은 기준으로 바꿔야 한다고 생각했다. 두 값은 기준이 다르다.
- `attributePaths`: 엔티티의 **필드 이름** (`category`)
- `@JoinColumn(name)`: DB의 **FK 컬럼 이름** (`category_id`)

그대로 두면 `ddl-auto: update`가 product 테이블에 NOT NULL `category` 컬럼을 새로 추가하려고 하고, `category_id`로 넣는 `data.sql`과도 맞지 않는다.

**해결**
`@JoinColumn(name = "category_id")`로 되돌렸다. 바뀐 컬럼으로 앱을 실행한 적이 있으면 로컬 DB에 `category` 컬럼이 남아 있을 수 있으니 테이블을 확인한다.

**배운 점**
- JPA 어노테이션 값이 **자바(엔티티/필드) 기준**인지 **DB(테이블/컬럼) 기준**인지 구분한다. JPQL, `attributePaths`, `mappedBy`는 자바 기준이고 `@Table`, `@Column`, `@JoinColumn`은 DB 기준이다.
- 하려는 수정과 상관없는 파일이 바뀌었으면 커밋 전에 `git diff`로 확인한다.

---
