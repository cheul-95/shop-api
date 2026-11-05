# 07. JWT 인증 필터

## 미션 요구사항

- 요청의 `Authorization: Bearer <token>`을 검증하는 필터
- 경로별로 허용 / 인증 필요 구분
- `GET /api/members/me` (내 정보 조회)
- 인증 실패 401, 권한 부족 403을 JSON으로 응답
- **완료 조건**: 토큰 없이 `/me` → 401 / 토큰과 함께 → 200 / 상품 조회는 토큰 없이 200

---

## 1. 구조와 요청 흐름

```
global/security/
├── jwt/
│   ├── JwtProvider.java                 (미션 5) + resolveToken 추가
│   └── JwtAuthenticationFilter.java     토큰 → SecurityContext
└── handler/
    ├── JwtAuthenticationEntryPoint.java 401 JSON
    └── JwtAccessDeniedHandler.java      403 JSON

domain/member/
├── controller/MemberController.java     GET /api/members/me
├── service/MemberService.java
└── dto/MemberResponse.java
```

```
요청
 → JwtAuthenticationFilter : 토큰이 유효하면 SecurityContext에 인증 정보 저장, 아니면 아무것도 안 함
 → AuthorizationFilter     : SecurityConfig의 경로 규칙으로 판단
      permitAll 경로                     → 통과
      authenticated 경로 + 인증 정보 있음 → 통과 → 컨트롤러
      authenticated 경로 + 인증 정보 없음 → AuthenticationEntryPoint → 401
      인증은 됐는데 권한 부족            → AccessDeniedHandler      → 403
```

- **필터는 "누구인지"를 확인하고, `authorizeHttpRequests`는 "들어와도 되는지"를 판단한다.** 역할이 나뉘어 있다.
- 필터, EntryPoint, AccessDeniedHandler는 모두 **DispatcherServlet보다 앞**에서 동작한다. 그래서 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 잡지 못하고, 응답을 직접 써야 한다.

## 2. `JwtAuthenticationFilter`

```java
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = jwtProvider.resolveToken(servletRequest);
        if (token != null) {
            try {
                Claims claims = jwtProvider.parse(token);
                Long memberId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        memberId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
                emptyContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(emptyContext);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("JWT 검증 실패: {}", e.getMessage());
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
```

### 필터는 401을 직접 보내지 않는다

- 토큰이 없거나 유효하지 않으면 **인증 정보를 저장하지 않고 그냥 다음 필터로 넘긴다.**
- 401 여부는 뒤의 인가 단계가 판단한다. 그래서 `permitAll`인 상품 조회는 토큰이 없어도, 이상한 토큰이 붙어 와도 200이다.
- 필터가 토큰 없음을 보자마자 401을 보내면 `GET /api/products`도 막힌다.

### 결정한 것들

- **`OncePerRequestFilter`**: 한 요청에 한 번만 실행된다. 일반 `Filter`는 forward/error dispatch 때 다시 실행될 수 있다.
- **`filterChain.doFilter`는 메서드 맨 끝**에 둔다. try 안에 두면 컨트롤러 예외까지 catch에 걸리고, if 안에 두면 토큰 없는 요청이 거기서 멈춘다.
- **principal은 `Long memberId`**만 넣었다. role은 authorities에 이미 있다. `Member` 엔티티를 넣으려면 매 요청마다 DB를 조회해야 해서, "DB 조회 없이 인증"이라는 JWT의 장점이 사라진다.
- **인자 3개짜리 `UsernamePasswordAuthenticationToken` 생성자**를 써야 `authenticated = true`가 된다. 2개짜리는 인증되지 않은 상태다.
- **`ROLE_` 접두사**: `hasRole("ADMIN")`은 내부적으로 `"ROLE_ADMIN"`과 비교한다. 토큰의 role은 `USER`라서 권한 문자열을 만들 때 붙였다.
- **`createEmptyContext()` → `setContext()`**: Spring Security 문서가 권장하는 방식이다. `getContext().setAuthentication()`보다 여러 스레드가 같은 컨텍스트를 공유할 때 안전하다.
- **catch는 `JwtException | IllegalArgumentException`** 하나로 했다.
  - 만료, 서명 오류, 형식 오류, 지원하지 않는 토큰이 모두 `JwtException`의 자식이다.
  - `IllegalArgumentException`이 잡는 경우: `"Bearer "` 뒤가 비어 있을 때 `parse("")`, sub가 숫자가 아닐 때 `Long.valueOf`의 `NumberFormatException`(자식 클래스), role 클레임이 없을 때 `new SimpleGrantedAuthority(null)`.
  - 로그는 `debug`로 남겼다. 잘못된 토큰은 흔한 일이라 운영에서 warn으로 찍으면 로그가 지저분해진다.

### `@Component`를 붙이지 않은 이유

- Spring Boot는 `Filter` 타입 빈을 **서블릿 필터로도 자동 등록**한다. `@Component`를 붙이면 Security 체인 안과 서블릿 컨테이너, 두 곳에 등록된다.
- `OncePerRequestFilter`가 "이미 실행했음" 표시를 확인하므로 실제로 두 번 실행되지는 않는다. 하지만 의도한 구조가 아니다.
- `SecurityConfig`에서 `new JwtAuthenticationFilter(jwtProvider)`로 직접 만들어 넣었다. (`@Component`를 유지하고 `FilterRegistrationBean.setEnabled(false)`로 자동 등록만 끄는 방법도 있다.)

### `resolveToken`의 위치

- `JwtProvider`에 뒀다. 토큰을 다루는 코드를 한곳에 모았다.
- 대신 `JwtProvider`가 `HttpServletRequest`에 의존하게 됐다. 헤더 해석은 필터의 일로 보고 필터의 private 메서드로 두는 방법도 있다. 그러면 `JwtProvider`는 서블릿을 몰라도 된다.

## 3. `SecurityConfig`: 경로별 규칙

```java
http
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(e -> e
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .anyRequest().authenticated()
        );
```

| 요청 | 걸리는 규칙 | 결과 |
|---|---|---|
| `POST /api/auth/signup`, `/login` | `/api/auth/**` | 토큰 없이 통과 |
| `GET /api/products`, `/api/products/{id}` | `GET /api/products/**` | 토큰 없이 통과 |
| `POST /api/products` (나중에 생기면) | GET 규칙에 안 걸림 → `anyRequest` | 인증 필요 |
| `GET /api/members/me` | `anyRequest` | 인증 필요 |
| 처리 못 한 예외 → `/error` forward | `/error` | 에러 응답이 정상적으로 나감 |

- 규칙은 **위에서부터 처음 맞는 것 하나만** 적용된다. `anyRequest()`는 항상 마지막이다.
- 상품은 `HttpMethod.GET`으로 **조회만** 열었다. 나중에 상품 등록/수정 API가 생기면 자동으로 인증 대상이 된다.
- `/api/products/**`는 `/api/products`(목록)도 포함한다. `/**`는 "경로 0개 이상"이다.
- `/error`를 막으면, 허용된 경로에서 500이 나서 `/error`로 forward될 때 그 요청이 막혀서 엉뚱하게 401/403이 나온다.
- **`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`**: 폼 로그인 필터는 꺼져 있지만, "인증 정보를 만드는 필터들" 자리의 기준점으로 쓴다. 인가 필터(`AuthorizationFilter`)보다 앞이어야 한다.
- 이 설정 체인에서 **메서드를 쓰는 순서는 필터 실행 순서와 관계없다.** 실행 순서는 Security가 정해둔 필터 순서와 `addFilterBefore`의 기준 클래스로 정해진다.

## 4. 401 / 403 JSON 응답

```java
@RequiredArgsConstructor
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;   // tools.jackson.databind

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }
}
```

`JwtAccessDeniedHandler`(`implements AccessDeniedHandler`, `handle(...)`)는 `ErrorCode.FORBIDDEN`만 다르고 같은 모양이다.

| 상황 | 처리 | ErrorCode |
|---|---|---|
| 인증 정보 없이 `authenticated()` 경로 | `AuthenticationEntryPoint` | `UNAUTHORIZED` 401 "로그인이 필요합니다." |
| 인증은 됐지만 권한 부족 (예: `hasRole("ADMIN")`) | `AccessDeniedHandler` | `FORBIDDEN` 403 "접근 권한이 없습니다." |

- **EntryPoint를 등록하지 않으면 401이 아니라 403이 나온다.** `formLogin`과 `httpBasic`을 끄면 Security가 기본으로 `Http403ForbiddenEntryPoint`를 쓰기 때문이다.
- `@Component`를 붙여도 Security가 이 빈들을 **자동으로 찾아 쓰지 않는다.** `exceptionHandling`에 직접 등록해야 한다. 서블릿 `Filter`가 아니라서 `@Component`를 붙여도 두 번 등록되는 문제는 없다.
- 컨트롤러가 아니라서 `ResponseEntity`를 반환할 수 없다. `HttpServletResponse`에 직접 쓴다.
- **`setCharacterEncoding("UTF-8")`은 `getWriter()`보다 먼저** 호출한다. writer는 만들어지는 시점의 인코딩을 쓴다. 빠뜨리거나 순서가 바뀌면 한글 메시지가 깨진다.
- 상태 코드를 `SC_UNAUTHORIZED`로 직접 쓰지 않고 `ErrorCode`에서 꺼냈다. 상태와 본문을 `ErrorCode` 한곳에서 관리한다.
- `ErrorResponse.of(...)`를 재사용해서 `GlobalExceptionHandler`와 같은 `{"code", "message"}` 형식으로 나간다.
- `objectMapper.writeValue(writer, 객체)`는 writer에 직접 쓰고 `void`를 반환한다. 문자열이 필요하면 `writeValueAsString`이다.
- **Spring Boot 4는 Jackson 3다.** 빈으로 등록되는 건 `tools.jackson.databind.json.JsonMapper`이고, `com.fasterxml.jackson.databind.ObjectMapper`(Jackson 2)를 주입받으려 하면 앱이 시작하지 않는다. [트러블슈팅 5](troubleshooting.md) 참고.

### 아직 구분하지 않는 것

- EntryPoint로 오는 경우는 ① 토큰 없음 ② 만료 ③ 위조/형식 오류 세 가지다. 필터가 예외를 삼키기 때문에 EntryPoint는 **이유를 모른다.** 지금은 모두 `UNAUTHORIZED`로 응답한다.
- `ErrorCode`에 `EXPIRED_TOKEN`, `INVALID_TOKEN`은 만들어 뒀지만 아직 쓰지 않는다.
- 구분하려면 필터의 catch에서 `request.setAttribute("jwtError", ErrorCode.EXPIRED_TOKEN)`처럼 이유를 남기고, EntryPoint에서 꺼내 쓰면 된다.

## 5. `GET /api/members/me`

```java
@GetMapping("/me")
public MemberResponse getMe(@AuthenticationPrincipal Long memberId) {
    return memberService.getMe(memberId);
}
```

```java
public MemberResponse getMe(Long memberId) {
    Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    return MemberResponse.from(member);
}
```

- **`@AuthenticationPrincipal`**은 필터에서 `UsernamePasswordAuthenticationToken`의 **첫 번째 인자**로 넣은 값을 꺼내 준다. `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`을 대신 해준다.
  - import는 `org.springframework.security.core.annotation.AuthenticationPrincipal`이다. `...security.web.bind.annotation`의 같은 이름은 deprecated다.
  - 필터에서 넣은 타입과 **파라미터 타입이 다르면 예외 없이 `null`**이 들어온다.
- `MemberResponse(id, email, name, role)`: `password`는 넣지 않았다. [트러블슈팅 4](troubleshooting.md) 참고.
- 토큰은 유효한데 회원이 DB에 없으면(탈퇴, DB 초기화) **404 `MEMBER_NOT_FOUND`**로 했다. 401("이 토큰은 더 이상 유효한 사용자를 가리키지 않는다")로 보는 방법도 있다. 기존 `ProductService.getProduct()`와 같은 패턴으로 가려고 404를 골랐다. 이 예외는 컨트롤러 안에서 나므로 `GlobalExceptionHandler`가 정상적으로 잡는다.
- 처음에는 `MemberRepository`에 `default Member getById(Long id)`를 만들어 `orElseThrow`를 넣었다. 그런데 `JpaRepository`에 이미 `getById`(deprecated, 지금은 `getReferenceById`)가 있어서 **그 메서드를 덮어쓰게 됐고** deprecated 경고가 났다. "없으면 어떤 에러인지"는 비즈니스 규칙이라 Service로 옮겼다.

## 6. 기존 테스트에 생긴 영향

`SecurityConfig`가 `JwtProvider`와 두 핸들러를 생성자로 받게 되면서 `GlobalExceptionHandlerTest`(`@WebMvcTest`) 6개가 깨졌다. `@MockitoBean JwtProvider`, 핸들러 `@Import`, `@WithMockUser`로 고쳤다. [트러블슈팅 6](troubleshooting.md) 참고.

## 7. 완료 조건 확인

앱을 실행하고 curl로 확인했다.

| 요청 | 결과 |
|---|---|
| `GET /api/members/me` (토큰 없음) | **401** `{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}` |
| `GET /api/members/me` (로그인으로 받은 토큰) | **200** `{"id":3,"email":"...","name":"tester","role":"USER"}` |
| `GET /api/members/me` (`Bearer abc.def.ghi`) | **401** (필터가 무시 → EntryPoint) |
| `GET /api/products`, `/api/products/1` (토큰 없음) | **200** |
| 401 응답 헤더 | `Content-Type: application/json;charset=UTF-8`, 한글 정상 |
| `./gradlew test` 전체 | 18개 통과 |

- [x] 토큰 없이 `/me` → 401
- [x] 토큰과 함께 `/me` → 200
- [x] 상품 조회는 토큰 없이 200
- [x] 403 JSON: ADMIN 전용 경로가 없어서 `requestMatchers("/api/admin/**").hasRole("ADMIN")`을 잠깐 추가하고 USER 토큰으로 호출해 403 JSON 응답을 확인했다. 확인한 뒤 임시 규칙은 되돌렸다.

## 8. 남은 것

- 보안 동작을 검증하는 테스트가 없다. 지금 테스트는 "보안 설정 때문에 기존 테스트가 깨지지 않는지"만 본다. `/me`를 토큰 없이 호출하면 401 JSON인지 MockMvc로 확인하는 테스트를 추가할 수 있다.
- 만료/위조 토큰을 구분한 401 응답 (4번 "아직 구분하지 않는 것").
- `jwt.refresh-token.expiration`은 여전히 쓰는 곳이 없다.
