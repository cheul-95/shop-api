# 01. 공통 코드

> 커밋: `916e8ea feat: 미션1 공통 코드 (BaseTimeEntity, ErrorCode, 전역 예외 처리)`

## 미션 요구사항

- BaseTimeEntity (생성일/수정일 자동 기록) + Auditing 켜기
- ErrorCode enum (HTTP 상태 + 메시지), BusinessException
- GlobalExceptionHandler: BusinessException, 입력값 검증 실패, 깨진 JSON → 모두 `{code, message}`
- **완료 조건**: 미션 3에서 없는 상품을 조회하면 404 `{"code":"PRODUCT_NOT_FOUND", ...}`가 나온다.

요구사항 외에 404(없는 URL), 405(지원하지 않는 메서드), 500(예상 못 한 예외)까지 처리했다.

> **미션 3에서 결정함: 에러 코드는 enum 이름으로 통일**
> 처음에는 `ErrorCode`에 `"0005"` 같은 숫자 코드를 따로 뒀는데, 완료 조건 예시는 `"PRODUCT_NOT_FOUND"`처럼 enum 이름이었다.
> - enum 이름 사용: `errorCode.name()`을 응답 → 별도 `code` 필드가 필요 없고, 읽기만 해도 의미를 안다.
> - 숫자 코드 유지: 코드 체계가 따로 있는 회사 규칙에 맞추기 쉽지만, 번호만 보고는 의미를 모른다.
>
> 따로 정해진 코드 체계가 없으니 **enum 이름**을 쓰기로 했다. `code` 필드를 지우고 `ErrorResponse`에서 `errorCode.name()`을 응답한다.

---

모든 도메인이 같이 쓰는 코드를 `global` 패키지에 먼저 만들었다.

## 1. BaseTimeEntity: 생성일/수정일 자동 기록

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

| 어노테이션 | 역할 |
|---|---|
| `@MappedSuperclass` | 이 클래스는 테이블이 아니다. 필드만 자식 엔티티의 컬럼으로 물려준다 |
| `@EntityListeners(AuditingEntityListener.class)` | 저장/수정 이벤트를 감지해서 날짜를 채워준다 |
| `@CreatedDate` | 처음 저장될 때 현재 시간 |
| `@LastModifiedDate` | 수정될 때마다 현재 시간 |
| `@Column(updatable = false)` | 생성일은 UPDATE 쿼리에 포함되지 않음 |
| `abstract` | 단독으로 `new` 할 일이 없음 |

- 동작하려면 `@EnableJpaAuditing`이 필요하다 → `JpaAuditingConfig`에 따로 뒀다.
- 엔티티는 `extends BaseTimeEntity`만 하면 `created_at`, `updated_at` 컬럼이 생긴다.
- PHP Laravel의 `timestamps()`와 같은 역할.

### @EnableJpaAuditing을 메인 클래스가 아니라 별도 Config에 둔 이유

`ShopApplication`에 붙이면 `@WebMvcTest` 같은 슬라이스 테스트에서도 JPA Auditing을 켜려고 시도한다.
슬라이스 테스트에는 JPA 관련 빈이 없어서 오류가 난다. 별도 `@Configuration`으로 분리하면 슬라이스 테스트에서 자동으로 제외된다.

## 2. ErrorCode: 에러 정보를 한 곳에서 관리

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "시스템 에러입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다"),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "수량은 1 이상이어야 합니다"),
    ALREADY_CANCELED_ORDER(HttpStatus.BAD_REQUEST, "이미 취소된 주문입니다"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
```

- HTTP 상태와 메시지를 **한 묶음**으로 관리한다. 에러 코드는 enum 이름(`OUT_OF_STOCK`) 자체를 쓴다.
- 에러 메시지를 코드 곳곳에 문자열로 흩어놓지 않아도 된다. 수정할 때 여기만 고치면 된다.
- 프론트엔드는 메시지 문구가 아니라 `code`로 분기 처리할 수 있다.
- enum 이름이 곧 API 응답 값이 되므로, 한번 공개한 이름은 함부로 바꾸면 안 된다.
- 도메인 에러가 생길 때마다 여기에 추가한다 (미션2에서 `OUT_OF_STOCK`, `INVALID_QUANTITY`, `ALREADY_CANCELED_ORDER`, 미션3에서 `PRODUCT_NOT_FOUND` 추가).

## 3. BusinessException: 비즈니스 예외

```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

- "재고 부족"처럼 **규칙상 허용되지 않는 상황**에 던지는 예외. `ErrorCode`를 담고 다닌다.
- 사용 예: `throw new BusinessException(ErrorCode.OUT_OF_STOCK);`

### RuntimeException을 상속한 이유

| | Checked Exception (`Exception`) | Unchecked Exception (`RuntimeException`) |
|---|---|---|
| 처리 강제 | `throws` 선언 또는 `try-catch` 필수 | 강제 안 함 |
| `@Transactional` 롤백 | 기본적으로 **롤백 안 됨** | 기본적으로 **롤백됨** |

비즈니스 예외는 대부분 "요청 실패 → 롤백 → 에러 응답"으로 끝난다.
메서드마다 `throws`를 붙일 필요가 없고 롤백도 자동으로 되는 `RuntimeException`이 맞다.
(보너스 미션의 `createOrder_partialOutOfStock_rollsBackAll`이 이 롤백 동작에 기대고 있다. [08. 주문 생성 API](08-order.md))

### BusinessException과 일반 RuntimeException의 차이

둘은 별개가 아니라 부모와 자식 관계다. 차이는 **누가 언제 던지고, 어떻게 처리하느냐**에 있다.

| | 일반 `RuntimeException` | `BusinessException` |
|---|---|---|
| 의미 | **예상하지 못한** 오류 (NPE, 잘못된 인덱스 접근, 코드 버그) | **예상한** 규칙 위반 (재고 부족, 상품 없음, 중복 이메일) |
| 가진 정보 | 메시지, 스택트레이스 | 추가로 `ErrorCode` (HTTP 상태, 코드, 메시지) |
| 받는 핸들러 | `@ExceptionHandler(Exception.class)` | `@ExceptionHandler(BusinessException.class)` |
| 응답 | 500 `SYSTEM_ERROR` + `log.error`로 스택트레이스 기록 | `ErrorCode`에 정한 상태(404, 409 등)와 메시지 |

- `RuntimeException` 전용 핸들러는 없다. 그래서 일반 런타임 예외는 `Exception` 핸들러로 떨어진다.
- 예상한 실패는 클라이언트가 알아야 할 정상적인 응답이라 로그를 남기지 않는다. 예상하지 못한 오류는 서버 로그에만 자세히 남기고, 클라이언트에는 내부 정보가 새지 않게 500만 준다.

**한 줄 정리**: `BusinessException`은 `RuntimeException`을 상속한 커스텀 예외다. 예상 가능한 도메인 규칙 위반을 `ErrorCode`와 함께 던져서 의미 있는 상태 코드로 응답한다. 그 외 예상하지 못한 예외는 500으로 처리하고 로그를 남긴다.

## 4. ErrorResponse: 에러 응답 형식

```java
public record ErrorResponse(String code, String message) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }
}
```

모든 에러 응답이 같은 형태로 나간다.

```json
{ "code": "OUT_OF_STOCK", "message": "재고가 부족합니다" }
```

### record

- Java 16+ 문법. 필드, 생성자, getter(`code()`), `equals`/`hashCode`/`toString`을 자동으로 만들어 준다.
- 값이 바뀌지 않는(불변) 데이터 전달용 객체에 적합하다. 응답 DTO에 잘 맞는다.
- Lombok의 `@Value`와 비슷하지만 자바 기본 문법이다. PHP 8.2의 `readonly class` + 생성자 프로모션과 거의 같다.

### record / interface / class / enum을 고르는 기준

이 프로젝트는 **역할**에 따라 나눴다.

| 종류 | 쓰는 곳 | 이유 |
|---|---|---|
| `record` | 모든 DTO (`OrderRequest`, `ProductResponse`, `ErrorResponse` 등) | 데이터를 담아서 옮기기만 한다. 불변이고 로직이 없다 |
| `interface` | 모든 Repository (`OrderRepository` 등) | 구현은 Spring Data JPA가 런타임에 프록시로 만들어 준다 |
| `class` | Entity, Service, Controller, Config, Filter, `BusinessException` | 상태 변경, 로직, 상속이 필요하다 |
| `enum` | `Role`, `OrderStatus`, `ErrorCode` | 정해진 값의 목록이다 |

- **record (DTO)**: 필드가 `private final`이고 생성자, getter, `equals/hashCode/toString`이 자동으로 생긴다. Jackson이 생성자로 역직렬화하고, `@NotNull` 같은 검증 어노테이션도 컴포넌트에 바로 붙는다.
- **interface (Repository)**: `JpaRepository`를 상속해서 메서드를 선언만 한다. 애플리케이션이 시작될 때 Spring Data가 구현체를 만들어 빈으로 등록하고, `findByEmail`처럼 메서드 이름으로 쿼리도 만든다.
- **class**
  - **Entity는 record로 만들 수 없다.** JPA는 기본 생성자로 객체를 만들고 필드를 채우는데, record는 기본 생성자가 없고 필드가 final이다. 지연 로딩용 프록시는 엔티티를 **상속**해서 만드는데, record는 `final`이라 상속할 수 없다. `product.decreaseStock()`처럼 상태가 바뀌어야 dirty checking이 동작한다.
  - **Service, Controller**: 의존성을 주입받는다. `@Transactional`이 붙으면 Spring이 클래스를 상속(CGLIB)한 프록시를 만든다.
  - **BusinessException**: `RuntimeException`을 상속해야 한다. record는 다른 클래스를 상속할 수 없다(interface 구현만 된다).
- **enum**: 문자열 상수보다 타입이 안전하고 오타가 컴파일 때 잡힌다. `ErrorCode`처럼 상수마다 필드(상태, 메시지)를 붙일 수 있다.

**한 줄 정리**: DTO는 불변 데이터 전달용이라 record, Repository는 Spring Data가 구현체를 만들어 주니 interface로 만들었다. Entity는 JPA가 기본 생성자, 프록시 상속, dirty checking을 써야 해서 class로 만들었다.

### 정적 팩토리 메서드 `of()`

`new ErrorResponse(errorCode.name(), errorCode.getMessage())` 대신 `ErrorResponse.of(errorCode)`로 의도가 드러나게 만든다.

## 5. GlobalExceptionHandler: 전역 예외 처리

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode());
    }

    // ... 검증 실패, 깨진 JSON, 404, 405 ...

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.SYSTEM_ERROR);
    }
}
```

- `@RestControllerAdvice`: 모든 컨트롤러에서 던져진 예외를 한 곳에서 잡는다. 컨트롤러마다 `try-catch`를 쓰지 않아도 된다.
- `@ExceptionHandler(XXX.class)`: 해당 예외(와 그 하위 예외)를 처리할 메서드.

| 예외 | 언제 발생 | 응답 |
|---|---|---|
| `BusinessException` | 직접 던진 비즈니스 예외 | ErrorCode에 지정한 상태 |
| `MethodArgumentNotValidException` | `@Valid` 검증 실패 (`@NotBlank` 등) | 400 `INVALID_INPUT` |
| `HttpMessageNotReadableException` | JSON 문법이 깨진 요청 | 400 `INVALID_JSON` |
| `NoResourceFoundException` | 없는 URL | 404 `NOT_FOUND` |
| `HttpRequestMethodNotSupportedException` | GET만 있는데 POST 요청 등 | 405 `METHOD_NOT_ALLOWED` |
| `Exception` | 위에서 안 잡힌 모든 예외 | 500 `SYSTEM_ERROR` + 에러 로그 |

### 예상 못 한 예외는 메시지를 그대로 내보내지 않는다

- `e.getMessage()`에는 SQL, 클래스명, 내부 경로 같은 **구현 정보**가 담길 수 있다. 그대로 응답하면 보안상 위험하다.
- 사용자에게는 고정된 `"시스템 에러입니다."`만 보내고, 실제 원인은 `log.error`로 서버 로그에만 남긴다.
- `log.error("...", e)`처럼 예외 객체를 마지막 인자로 넘겨야 스택 트레이스까지 찍힌다.

### 더 구체적인 예외가 우선

`Exception` 핸들러가 있어도 `BusinessException`은 `handleBusiness`로 간다. Spring은 **가장 구체적인 타입**의 핸들러를 고른다.

## 6. GlobalExceptionHandlerTest

`@WebMvcTest`로 6가지 예외 응답을 검증했다. 자세한 내용은 [03. 테스트](03-testing.md) 참고.
