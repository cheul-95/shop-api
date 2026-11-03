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

## 4. 로그인 응답에 비밀번호 해시가 나감

**상황**
회원가입/로그인 응답 DTO를 `Member`의 필드를 그대로 옮겨서 만들었다.

```java
public record AuthResponse(String email, String password, String name) {
    public static AuthResponse from(Member member) {
        return new AuthResponse(member.getEmail(), member.getPassword(), member.getName());
    }
}
```

가입/로그인 응답에 `"password":"$2a$10$..."`가 그대로 들어갔다.

**원인**
"엔티티 대신 DTO로 응답한다"는 것만 지키고, DTO에 어떤 필드를 담을지는 따져보지 않았다. DTO가 엔티티 필드를 그대로 복사하면 엔티티를 응답하는 것과 노출 범위가 같다.

BCrypt 해시는 복호화가 안 되지만, 노출되면 오프라인에서 무차별 대입 공격을 할 수 있다. 응답 로그, 브라우저 개발자 도구, 프록시 등 여러 곳에 남기도 한다.

**해결**
`AuthResponse`에서 `password`를 뺐다.

```java
public record AuthResponse(String email, String name) { ... }
```

**배운 점**
- 응답 DTO는 엔티티 기준이 아니라 **클라이언트가 필요한 값** 기준으로 필드를 고른다.
- 앱을 띄워 실제 응답 JSON을 한 번은 눈으로 확인한다.

---


## 5. 이름이 같은 다른 패키지의 클래스를 import함

**상황**
미션 6에서 같은 실수를 네 번 했다. 이름은 같은데 패키지가 다른 클래스를 IDE 자동 import가 골랐다.

| 클래스 | 잘못 들어온 것 | 맞는 것 | 증상 |
|---|---|---|---|
| `SignatureException` | `java.security` | `io.jsonwebtoken.security` | 컴파일 에러 |
| `SignatureException` | `io.jsonwebtoken` (`io.jsonwebtoken.*`로 들어옴) | `io.jsonwebtoken.security` | deprecated 경고 |
| `AuthenticationException` | `javax.security.sasl` | `org.springframework.security.core` | `@Override` 컴파일 에러 |
| `ObjectMapper` | `com.fasterxml.jackson.databind` (Jackson 2) | `tools.jackson.databind` (Jackson 3) | 실행 전 리뷰에서 발견 |

첫 번째 경우의 컴파일 에러:
```
error: exception SignatureException is never thrown in body of corresponding try statement
            } catch (SignatureException e){
```

**원인**
- `java.security.SignatureException`은 JDK의 **checked 예외**다. try 블록 안에서 던질 수 없는 checked 예외를 catch하면 컴파일러가 막는다. jjwt가 던지는 건 `io.jsonwebtoken.security.SignatureException`(unchecked)이다.
- `io.jsonwebtoken.SignatureException`은 jjwt 0.12에서 deprecated됐다. `io.jsonwebtoken.*` 와일드카드 import 때문에 이쪽이 들어왔다.
- `javax.security.sasl.AuthenticationException`은 JDK의 SASL 인증용 예외라 Spring Security와 관계가 없다.
- Spring Boot 4는 Jackson 3를 쓰고, 빈으로 등록되는 것도 `tools.jackson`의 `JsonMapper`다. Jackson 2의 `ObjectMapper`를 주입받으려 하면 빈을 찾지 못해 앱이 시작하지 않는다.

**해결**
- jjwt 예외는 catch를 `JwtException | IllegalArgumentException` 하나로 합쳤다. `SignatureException`, `ExpiredJwtException`, `MalformedJwtException`이 모두 `JwtException`의 자식이라 개별 import가 필요 없어졌다.
- `implements AuthenticationEntryPoint`를 먼저 쓰고 IDE의 "Implement methods"로 메서드를 생성했다. 시그니처와 함께 올바른 import가 들어온다.
- `ObjectMapper`는 `tools.jackson.databind.ObjectMapper`로 바꿨다.

**배운 점**
- 자동 import 후에는 **패키지를 한 번 확인한다.** 특히 `Exception`, `ObjectMapper`처럼 흔한 이름은 후보가 여러 개다.
- 와일드카드 import(`.*`)는 비슷한 이름의 클래스가 섞여 들어오는 원인이 된다.
- 인터페이스를 구현할 때는 메서드를 직접 치지 말고 IDE로 생성한다.
- 메이저 버전이 바뀐 라이브러리(Jackson 2 → 3, jjwt 0.11 → 0.12)는 패키지까지 바뀌었을 수 있다.

---

## 6. SecurityConfig에 의존성을 추가하자 `@WebMvcTest`가 깨짐

**상황**
JWT 필터와 401/403 핸들러를 `SecurityConfig`에 등록한 뒤 `./gradlew test`를 돌리자, 미션 6과 상관없는 `GlobalExceptionHandlerTest`의 6개가 모두 실패했다.

```
18 tests completed, 6 failed

Caused by: NoSuchBeanDefinitionException:
  No qualifying bean of type 'com.lsch.shop.global.security.jwt.JwtProvider' available
Error creating bean with name 'com.lsch.shop.global.config.SecurityConfig':
  Unsatisfied dependency expressed through constructor parameter 0
```

**원인**
원인이 두 단계였다.

1. **컨텍스트 로딩 실패**: 테스트는 `@Import(SecurityConfig.class)`로 보안 설정을 직접 넣고 있었다. 그런데 `SecurityConfig`가 생성자로 `JwtProvider`, `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`를 받게 바뀌었다. `@WebMvcTest`는 컨트롤러와 `@ControllerAdvice` 같은 웹 계층 빈만 띄우는 슬라이스 테스트라 일반 `@Component`는 만들지 않는다. 주입할 빈이 없으니 `SecurityConfig`를 만들지 못했다.
2. **인증 필요**: 1을 고쳐도 테스트용 경로 `/test/**`는 `anyRequest().authenticated()`에 걸린다. 토큰 없이 요청하면 모두 401이 된다.

**해결**
```java
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
         GlobalExceptionHandlerTest.TestController.class, SecurityConfig.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    @MockitoBean
    private JwtProvider jwtProvider;
```

| 대상 | 방법 | 이유 |
|---|---|---|
| `JwtProvider` | `@MockitoBean` (가짜) | 진짜를 만들려면 `jwt.secret` 설정이 필요하다. 이 테스트는 JWT를 쓰지 않으므로 자리만 채우면 된다 |
| 401/403 핸들러 | `@Import` (진짜) | 의존하는 `ObjectMapper`는 `@WebMvcTest`가 띄워준다 |
| 401 문제 | `@WithMockUser` | 테스트 실행 전에 가짜 인증 정보를 SecurityContext에 넣어준다. 토큰이 없으니 JWT 필터는 아무것도 하지 않고, 인가 단계는 미리 들어 있는 인증 정보를 보고 통과시킨다 |

18개 모두 통과했다.

**배운 점**
- 슬라이스 테스트는 **띄우는 빈의 범위가 정해져 있다.** 직접 `@Import`한 설정 클래스의 의존성이 늘어나면 테스트도 같이 챙겨야 한다.
- 공통 설정(보안, 설정 클래스)을 바꾸면 관련 없어 보이는 테스트도 깨질 수 있다. 기능을 추가한 뒤 전체 테스트를 돌린다.
- 에러가 겹쳐 있을 때는 단계를 나눠 본다. 첫 에러(컨텍스트 로딩)를 고친 뒤에야 두 번째 에러(401)가 보인다.

---
