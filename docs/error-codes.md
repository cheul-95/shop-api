# 에러 코드와 HTTP 상태

이 프로젝트의 모든 에러 응답과, 상태 코드를 고른 기준을 정리한 문서다.
코드 기준: `global/error/ErrorCode.java`

## 1. 응답 형식

모든 에러는 상태 코드와 함께 같은 형식의 JSON으로 응답한다.

```json
{ "code": "OUT_OF_STOCK", "message": "재고가 부족합니다" }
```

- `code`: `ErrorCode` enum의 이름. 클라이언트는 메시지가 아니라 **code로 분기**한다. 메시지는 바뀔 수 있다.
- `message`: 사용자에게 보여 줄 수 있는 문장. 내부 정보(스택트레이스, SQL)는 넣지 않는다.

## 2. 에러 코드 전체 목록

| 상태 | code | message | 발생 위치 | 언제 |
|---|---|---|---|---|
| 400 | `INVALID_INPUT` | 입력값이 올바르지 않습니다. | `GlobalExceptionHandler` | `@Valid` 검증 실패 (`MethodArgumentNotValidException`) |
| 400 | `INVALID_JSON` | 요청 형식이 올바르지 않습니다. | `GlobalExceptionHandler` | JSON 문법 오류, 타입 불일치 (`HttpMessageNotReadableException`) |
| 400 | `INVALID_QUANTITY` | 수량은 1 이상이어야 합니다 | `Product.decreaseStock`, `increaseStock` | 수량 0 이하. API에서는 `@Min(1)`이 먼저 막아서 `INVALID_INPUT`이 나가고, 이 코드는 API를 거치지 않는 경로를 지킨다 |
| 400 | `ALREADY_CANCELED_ORDER` | 이미 취소된 주문입니다 | `Order.cancel` | 취소된 주문을 다시 취소 (취소 API는 아직 없음) |
| 401 | `UNAUTHORIZED` | 로그인이 필요합니다. | `JwtAuthenticationEntryPoint` | 인증이 필요한 API에 토큰이 없거나 유효하지 않음 |
| 401 | `FAIL_LOGIN` | 로그인에 실패하셨습니다. ... | `AuthService.login` | 없는 이메일 **또는** 틀린 비밀번호 (둘을 구분하지 않음) |
| 401 | `EXPIRED_TOKEN` | 로그인이 만료되었습니다. ... | - | **미사용.** 만료 토큰도 지금은 `UNAUTHORIZED` |
| 401 | `INVALID_TOKEN` | 유효한 토큰이 아닙니다. ... | - | **미사용.** 위조/형식 오류 토큰도 지금은 `UNAUTHORIZED` |
| 403 | `FORBIDDEN` | 접근 권한이 없습니다. | `JwtAccessDeniedHandler` | 인증은 됐지만 권한(Role)이 부족함 |
| 404 | `NOT_FOUND` | 요청한 리소스를 찾을 수 없습니다. | `GlobalExceptionHandler` | 없는 URL (`NoResourceFoundException`) |
| 404 | `PRODUCT_NOT_FOUND` | 상품을 찾을 수 없습니다. | `ProductService`, `OrderService` | 없는 상품 ID로 조회하거나 주문 |
| 404 | `MEMBER_NOT_FOUND` | 회원을 확인 할 수 없습니다. | `MemberService`, `OrderService` | 토큰은 유효하지만 회원이 없음 (탈퇴 등) |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드입니다. | `GlobalExceptionHandler` | 예: `DELETE /api/products` |
| 409 | `DUPLICATE_EMAIL` | 중복된 이메일 입니다. | `AuthService.signup` | 이미 가입된 이메일로 회원가입 |
| 409 | `OUT_OF_STOCK` | 재고가 부족합니다 | `Product.decreaseStock` | 주문 수량이 재고보다 많음 |
| 500 | `SYSTEM_ERROR` | 시스템 에러입니다. | `GlobalExceptionHandler` | 위에서 처리하지 못한 모든 예외. 서버에 `log.error`로 스택트레이스를 남김 |

## 3. 상태 코드를 고른 기준

### 4xx와 5xx

- **4xx**: 클라이언트가 요청을 바꾸면 해결되는 문제. 예상한 실패라서 `BusinessException`으로 던지고, 에러 로그는 남기지 않는다.
- **5xx**: 서버 쪽 문제. 클라이언트가 해결할 수 없다. 예상하지 못한 예외라 로그를 남기고, 원인은 응답에 넣지 않는다.

### 400 Bad Request: 요청 자체가 잘못됨

- 서버 상태와 상관없이 **요청만 보고 틀렸다고 판단**할 수 있는 경우다. 같은 요청은 언제 보내도 실패한다.
- 예: 수량 0, 필수값 누락, 깨진 JSON

### 401 Unauthorized vs 403 Forbidden

| | 401 | 403 |
|---|---|---|
| 뜻 | **누구인지 모름** (인증 실패) | **누군지는 알지만 권한이 없음** (인가 실패) |
| 해결 | 로그인하거나 토큰을 다시 받으면 됨 | 다시 로그인해도 같음 |
| 처리 위치 | `AuthenticationEntryPoint` | `AccessDeniedHandler` |

- 이름이 `Unauthorized`라서 헷갈리지만, 401은 실제로는 "인증되지 않음(unauthenticated)"이라는 뜻이다.
- `formLogin`과 `httpBasic`을 끄고 EntryPoint를 등록하지 않으면, Security는 401이 아니라 **403**을 응답한다. → [07. JWT 인증 필터](07-jwt-filter.md)
- 로그인 실패(`FAIL_LOGIN`)는 "이메일 없음"과 "비밀번호 틀림"을 같은 401로 응답한다. 둘을 나누면 응답만 보고 가입된 이메일을 알아낼 수 있기 때문이다(계정 열거 방지). → [05. 회원가입 / 로그인 API](05-auth-api.md)

### 404 Not Found

- 요청한 대상이 없는 경우다. URL이 없을 때(`NOT_FOUND`)와 데이터가 없을 때(`PRODUCT_NOT_FOUND`)를 code로 구분한다.
- 주문 요청 본문에 있는 상품 ID가 없을 때도 404를 쓴다. 422(Unprocessable Content)를 쓰는 API도 있지만, 이 프로젝트는 "참조한 리소스가 없음"을 404로 통일했다.

### 409 Conflict: 요청은 맞지만 서버의 현재 상태와 충돌

- 요청 형식은 문제가 없는데 **지금 서버 상태 때문에** 처리할 수 없는 경우다. 상태가 바뀌면 같은 요청이 성공할 수 있다.
- `DUPLICATE_EMAIL`: 같은 이메일이 이미 있음
- `OUT_OF_STOCK`: 미션 2에서는 400이었는데 보너스 미션에서 409로 바꿨다. 재고가 있을 때는 같은 요청이 성공하기 때문이다. → [08. 주문 생성 API](08-order.md)

### 201 Created (성공이지만 함께 정리)

- 리소스를 만든 API(회원가입, 주문 생성)는 200이 아니라 201로 응답한다. `ResponseEntity.status(HttpStatus.CREATED)`

## 4. 예외가 응답이 되는 경로

```
요청
 ├─ 필터 단계 (컨트롤러 도달 전)
 │    인증 실패 → JwtAuthenticationEntryPoint → 401 UNAUTHORIZED
 │    권한 부족 → JwtAccessDeniedHandler     → 403 FORBIDDEN
 │    ※ @RestControllerAdvice는 필터 예외를 잡지 못해서 따로 처리한다
 │
 └─ 컨트롤러 / 서비스 / 엔티티
      BusinessException(ErrorCode)          → GlobalExceptionHandler → ErrorCode의 상태
      Spring 예외 (검증, JSON, 404, 405)     → GlobalExceptionHandler → 해당 ErrorCode
      그 외 모든 예외                         → GlobalExceptionHandler → 500 SYSTEM_ERROR + 로그
```

`BusinessException`과 일반 `RuntimeException`의 차이 → [01. 공통 코드](01-common-code.md)

## 5. 새 에러 코드를 추가할 때

1. 위 기준으로 상태 코드를 고른다. 400인지 409인지 헷갈리면 "서버 상태가 바뀌면 같은 요청이 성공하는가?"를 묻는다.
2. `ErrorCode`에 `이름(HttpStatus, "메시지")`를 추가한다. 이름은 클라이언트에 그대로 나가는 code라서, 한번 공개하면 바꾸기 어렵다.
3. 도메인 규칙 위반이면 `throw new BusinessException(ErrorCode.XXX)`
4. 이 문서의 목록과 README의 에러 응답 표를 갱신한다.
5. 테스트에서는 예외 타입만 보지 말고 `errorCode`까지 검증한다.

```java
assertThatThrownBy(() -> ...)
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
```

## 6. 검토할 것

- `ALREADY_CANCELED_ORDER`는 지금 400이다. "이미 취소된 상태와 충돌"이라고 보면 `OUT_OF_STOCK`과 같은 이유로 409가 더 일관된다. 주문 취소 API를 만들 때 정한다.
- `EXPIRED_TOKEN`, `INVALID_TOKEN`은 만들어 두었지만 쓰지 않는다. 필터에서 실패 이유를 request attribute에 담고, EntryPoint에서 꺼내서 구분하는 방법이 있다.
- `@PathVariable` 타입 불일치(`/api/products/abc`)는 전용 핸들러가 없어서 지금은 500으로 갈 수 있다. 확인 후 400으로 처리할지 정한다.
