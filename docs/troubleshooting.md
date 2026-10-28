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
