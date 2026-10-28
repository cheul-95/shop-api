# 03. 테스트

## 1. 테스트 종류

이 프로젝트에는 세 종류의 테스트가 있다.

| 종류 | 예시 | 스프링 실행 | DB | 속도 |
|---|---|---|---|---|
| 단위 테스트 | `ProductTest`, `OrderTest` | X (순수 자바) | X | 매우 빠름 |
| 슬라이스 테스트 | `GlobalExceptionHandlerTest` (`@WebMvcTest`) | 웹 계층만 | X | 빠름 |
| 통합 테스트 | `ShopApplicationTests` (`@SpringBootTest`) | 전체 | O (Testcontainers) | 느림 |

- **단위 테스트**: 클래스 하나의 로직만 검증. `new`로 객체를 만들어서 메서드를 호출하고 결과를 확인한다. 엔티티의 비즈니스 로직(`decreaseStock()`, `addItem()`)을 검증하기 좋다.
- **슬라이스 테스트**: 특정 계층만 띄운다. `@WebMvcTest`는 컨트롤러, `@ControllerAdvice`, 필터 등 웹 관련 빈만 올린다.
- **통합 테스트**: 애플리케이션 전체를 띄운다. 설정이나 엔티티 매핑이 잘못되면 여기서 걸린다.

가능하면 단위 테스트로 검증하고, 꼭 필요할 때만 범위를 넓힌다.

## 2. 테스트 실행 방법

```powershell
# 전체
.\gradlew.bat test

# 클래스 하나
.\gradlew.bat test --tests "com.lsch.shop.domain.product.entity.ProductTest"

# 메서드 하나
.\gradlew.bat test --tests "com.lsch.shop.domain.order.entity.OrderTest.addItem_calculatesTotalPrice"

# 결과 재사용하지 않고 강제로 다시 실행
.\gradlew.bat test --rerun-tasks

# 빌드 결과물 지우고 처음부터
.\gradlew.bat clean test
```

- 결과 리포트: `build/reports/tests/test/index.html`
- 코드 변경이 없으면 Gradle이 이전 결과를 재사용한다(`UP-TO-DATE`). 다시 돌리려면 `--rerun-tasks`.
- IntelliJ: 클래스/메서드 옆 ▶ 클릭, 또는 `Ctrl + Shift + F10`.

## 3. AssertJ 기본

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

```java
assertThat(product.getStock()).isEqualTo(7);
assertThat(order.getItems()).hasSize(2);
assertThat(notebookItem.getOrder()).isEqualTo(order);
```

- `assertThat(실제값).검증메서드(기대값)` 형태. 틀리면 `expected: 7 but was: 10` 같은 메시지와 함께 테스트가 실패한다.
- `spring-boot-starter-test`(Boot 4에서는 각 `*-test` 스타터)에 포함되어 있다.
- `testImplementation`으로 들어온 라이브러리라 **`src/test`에서만** 쓸 수 있다. `src/main`에서 import하면 컴파일 오류.
- PHP로 비유하면 PHPUnit의 `$this->assertEquals()`.

### `Assertions` vs `AssertionsForClassTypes`

IntelliJ 자동 import가 `AssertionsForClassTypes`를 고르는 경우가 많다.
이건 일반 객체용 기능만 있는 축소판이라 `hasSize()`, `contains()` 같은 컬렉션 검증 메서드가 없다.
**항상 `org.assertj.core.api.Assertions`를 import한다.**

### JUnit의 Assertions와 섞지 않기

`org.junit.jupiter.api.Assertions.*`(JUnit 기본 assert)와 AssertJ를 섞으면 헷갈린다. AssertJ로 통일한다.

### 실제 코드(main)에서 검증할 때는

`assertThat`이 아니라 `if` + 예외를 쓴다.

```java
if (quantity <= 0) {
    throw new BusinessException(ErrorCode.INVALID_QUANTITY);
}
```

자바의 `assert` 키워드는 기본적으로 꺼져 있어서(JVM 옵션 `-ea`가 필요) 실무 검증용으로는 거의 쓰지 않는다.

## 4. 예외 검증: `assertThatThrownBy`

### JUnit이 성공/실패를 판단하는 기준

| 테스트 안에서 일어난 일 | 결과 |
|---|---|
| 끝까지 실행, assert 모두 통과 | 성공 |
| assert 불일치 | 실패 |
| **잡히지 않은 예외 발생** | 실패 |

JUnit은 예외가 의도한 것인지 버그인지 모른다. "예외가 나와야 정상"인 테스트는 **그걸 직접 적어줘야** 한다.

```java
// X: 예외가 테스트 밖으로 터져서 실패
@Test
void invalidStock() {
    Product product = new Product("노트북", category, 1_000_000, 5);
    product.decreaseStock(10);
}

// O
@Test
void decreaseStock_outOfStock() {
    Product product = new Product("노트북", category, 1_000_000, 5);

    assertThatThrownBy(() -> product.decreaseStock(10))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
}
```

- `() -> ...` 람다로 감싸서 **실행 자체를** `assertThatThrownBy`에 넘긴다. 그래야 그 안에서 예외를 잡을 수 있다. 람다 없이 바로 호출하면 넘기기 전에 예외가 터진다.
- `isInstanceOf`만 보면 "재고 부족"인지 "잘못된 수량"인지 구분할 수 없다 → `errorCode`까지 확인한다.
- PHPUnit의 `$this->expectException()`과 같은 개념.
- JUnit 기본 문법으로는 `assertThrows(BusinessException.class, () -> ...)`.

**장점**: 누가 실수로 검증 `if`문을 지우면 예외가 **안 나서** 테스트가 실패한다. 검증 로직이 살아있는지까지 지켜준다.

## 5. 엔티티 단위 테스트 예시

### ProductTest

| 테스트 | 입력 | 기대 결과 |
|---|---|---|
| 정상 차감 | 재고 10, 차감 3 | 재고 7 |
| 수량 0 | 차감 0 | `INVALID_QUANTITY` |
| 수량 음수 | 차감 -5 | `INVALID_QUANTITY` |
| 재고 부족 | 재고 5, 차감 10 | `OUT_OF_STOCK` |

경계값(0), 잘못된 값(음수), 정상값, 한계 초과를 모두 확인한다.

### OrderTest

```java
@Test
void addItem_calculatesTotalPrice() {
    Member member = new Member("test@test.com", "test", "테스터");
    Order order = new Order(member);
    Category category = new Category("전자제품");

    Product notebook = new Product("노트북", category, 1_000_000, 100);
    Product mouse = new Product("마우스", category, 30_000, 100);

    OrderItem notebookItem = new OrderItem(notebook, 2);
    OrderItem mouseItem = new OrderItem(mouse, 1);

    order.addItem(notebookItem);
    order.addItem(mouseItem);

    assertThat(order.getTotalPrice()).isEqualTo(2_030_000);
    assertThat(order.getItems()).hasSize(2);
    assertThat(notebookItem.getOrder()).isEqualTo(order);   // 양방향 연결 확인
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
}
```

- 수량을 2개 이상으로 둬야 "수량을 곱하는지"를 검증할 수 있다. 전부 1개면 곱하지 않는 버그를 못 잡는다.
- 재고(100)와 주문 수량(2)을 다르게 둬야 `getStock()`과 `getQuantity()`를 혼동한 버그를 잡는다.

## 6. 좋은 테스트 작성 규칙

### assert가 없는 테스트는 아무것도 검증하지 않는다

예외 없이 끝나기만 하면 통과한다. 금액 계산 줄을 통째로 지워도 통과한다.

### 테스트가 버그를 잡는지 직접 확인하기

1. 검증하려는 코드를 일부러 망가뜨린다 (예: `addItem()`의 금액 계산 줄 주석 처리)
2. 테스트를 돌려서 **실패하는지** 확인한다
3. 원상복구한다

실패하지 않으면 그 테스트는 아무것도 지켜주지 못하는 것이다.

### 이름 짓기

- 메서드명은 **무엇을 검증하는지** 드러나게: `addItem_calculatesTotalPrice`, `decreaseStock_outOfStock`
- 클래스명과 같은 메서드명(`OrderTest()`)은 생성자처럼 보이므로 피한다.
- 테스트 이름과 내용이 일치해야 한다 (`outofStock()`인데 수량 0을 검사하면 X).
- 한글 메서드명도 가능하다: `비즈니스_예외는_ErrorCode의_상태와_메시지로_응답한다()`
- 또는 `@DisplayName("재고보다 많이 차감하면 OUT_OF_STOCK 예외")`

### 기타

- 테스트 클래스는 `public` 없이 `class`만 써도 된다 (JUnit 5).
- 테스트 패키지는 메인 코드와 **똑같이** 맞춘다 (대소문자까지). 그래야 package-private 멤버에도 접근할 수 있다.
- 변수명은 소문자로 시작하고, 비슷한 이름은 구분되게: `notebook`(Product) / `notebookItem`(OrderItem)
- 테스트 데이터에 실제 이름, 이메일 대신 더미 값을 쓴다: `test@test.com`, `"테스터"`
- 숫자는 `1_000_000`처럼 `_`로 끊어서 읽기 쉽게.

## 7. 앞으로 작성할 테스트 (미션 계획)

| 미션 | 테스트 | 검증할 것 |
|---|---|---|
| 2 (남은 과제) | `OrderTest` 추가 | 주문 생성 시 재고 차감, `cancel()` 시 재고 복구 |
| 5 | `JwtProviderTest` | 정상 토큰 / 만료 / 다른 키로 서명 / 형식 오류 |
| 보너스 | 주문 생성 | `@Transactional` 유무에 따라 재고가 실제로 줄어드는지 |

## 8. 슬라이스 테스트: GlobalExceptionHandlerTest

```java
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandlerTest.TestController.class, SecurityConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_메시지로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));
    }

    @RestController
    static class TestController {
        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
```

- 아직 실제 컨트롤러가 없어서, 테스트 안에 **예외만 던지는 테스트용 컨트롤러**를 만들어 검증했다.
- `MockMvc`: 실제 서버를 띄우지 않고 HTTP 요청/응답을 흉내 낸다.
- `jsonPath("$.code")`: 응답 JSON의 `code` 필드 값을 확인.
- `SecurityConfig`를 `@Import`한 이유: `@WebMvcTest`도 Spring Security를 적용하는데, 직접 만든 설정을 안 넣으면 기본 보안 설정이 적용돼서 요청이 401/403으로 막힌다.
- 500 테스트에서는 내부 메시지(`"내부 구현 정보가 담긴 메시지"`)가 아니라 `"시스템 에러입니다."`가 나가는지 확인한다 → 구현 정보가 응답에 노출되지 않는 것을 검증.
