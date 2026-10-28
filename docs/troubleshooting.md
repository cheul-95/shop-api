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

## 2. 테이블명 `order` 예약어

**상황**
`@Table(name = "order")`로 엔티티를 만들었다.

**원인**
`ORDER`는 SQL 예약어(`ORDER BY`)라서 `create table order (...)`가 문법 오류로 실패한다.

**해결**
`@Table(name = "orders")`로 변경했다.

**배운 점**
`order`, `user`, `group` 같은 예약어는 테이블명으로 쓰지 않는다. 복수형으로 짓는 것이 관례다.

---

## 3. 가격 필드를 `String`으로 선언

**상황**
`OrderItem.orderPrice`를 `String`으로 선언했다.

**원인**
가격은 `orderPrice * quantity`, 총액 합산 같은 계산에 쓰이는데 `String`으로는 계산할 수 없다.

**해결**
`int`로 변경했다. (`Product.price`와 같은 타입)

---

## 4. 주문 총액 계산 버그 (3단계)

`Order.addItem()`의 총액 계산을 고치면서 버그가 세 번 나왔다.

| 단계 | 코드 | 문제 |
|---|---|---|
| 1 | `totalPrice += item.getProduct().getPrice();` | **수량을 곱하지 않음**. 노트북 3개를 담아도 1개 가격 |
| 2 | `totalPrice += item.getProduct().getPrice() * item.getProduct().getStock();` | **재고(stock)를 곱함**. 재고 50개인 100만 원 노트북 2개 주문 → 5,000만 원 |
| 3 | `totalPrice += item.getProduct().getPrice() * item.getQuantity();` | 동작은 맞지만 현재 상품 가격을 사용 |
| 최종 | `totalPrice += item.getOrderPrice() * item.getQuantity();` | 주문 당시 가격 × 주문 수량 |

**원인**
- `stock`(창고에 남은 재고)과 `quantity`(고객이 주문한 수량)를 혼동했다.
- 주문 당시 가격을 저장하려고 `orderPrice`를 만들어 놓고 정작 계산에는 쓰지 않았다.

**배운 점**
- 주문 금액에 필요한 값(가격, 수량)은 모두 `OrderItem`에 있다. `item.getProduct()`를 거칠 필요가 없다.
- 이런 실수는 눈으로는 잘 안 보이지만 테스트로는 바로 잡힌다. **수량 ≥ 2**, **재고 ≠ 주문 수량**으로 테스트 데이터를 만들어야 잡을 수 있다.

---

## 5. 생성자 파라미터를 받아놓고 무시함

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

## 6. 폴더 대소문자 변경 후 "No matching tests found"

**상황**
테스트 폴더 `domain/Order/entity`를 `domain/order/entity`로 이름을 바꾼 뒤 테스트를 실행했다.

```
No matching tests found in any candidate test task.
    Requested tests:
        Test pattern com.lsch.shop.domain.order.entity.OrderTest.OrderTest in task :test
BUILD FAILED
5 actionable tasks: 1 executed, 4 up-to-date
```

**원인**
Windows는 파일 이름의 대소문자를 구분하지 않는다. Gradle이 대소문자만 바뀐 걸 "변경 없음"으로 판단해서(`up-to-date`) 다시 컴파일하지 않았다. 컴파일된 클래스는 옛 패키지(`domain.Order.entity`)로 남아 있었고, 새 패키지 이름으로는 찾을 수 없었다.

**해결**
```powershell
.\gradlew.bat clean test        # 또는 --rerun-tasks
```
IntelliJ에서는 Build → Rebuild Project.

**배운 점**
- 패키지/폴더 이름을 바꾼 뒤 이상하면 빌드 결과물을 지우고 다시 빌드한다.
- 패키지명은 처음부터 소문자로 짓는다. Linux(CI, 배포 서버)는 대소문자를 구분하므로 Windows에서만 통과하는 문제가 생길 수 있다.
- 테스트 패키지를 메인 코드와 다르게 쓰면 package-private 멤버(`setOrder()` 등)에 접근할 수 없다.

---

## 7. GitHub 저장소에 README 커밋이 있어서 푸시 불가

**상황**
GitHub에 저장소를 만들 때 README를 같이 생성했더니, 원격에 `Initial commit`이 생겨서 로컬 히스토리와 달랐다.

**해결**
원격 README가 자동 생성된 2줄짜리였고 로컬에 README.md가 이미 있어서, `--force-with-lease`로 원격을 덮어썼다.

**배운 점**
기존 프로젝트를 올릴 때는 GitHub 저장소를 **빈 상태**로 만든다 (README, .gitignore, license 체크 해제).
