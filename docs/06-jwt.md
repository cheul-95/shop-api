# 06. JWT 발급

> 커밋: `ebf7be6 feat: 미션5 JWT 발급 (JwtProvider, 로그인 응답 TokenResponse)`, `7cc36ca feat: 미션5 JWT 검증/파싱 + JwtProviderTest, .env.example 추가`

## 미션 요구사항

- jjwt로 Access Token 발급
- secret과 만료시간은 yml로 관리
- 로그인 응답을 `{accessToken, tokenType, expiresIn}`으로 변경
- **완료 조건**: jwt.io에서 `sub`, `role` 확인 / `JwtProviderTest`(정상, 만료, 다른 키, 형식 오류) 통과

---

## 1. 구조

```
AuthController  →  AuthService  →  MemberRepository
                        │
                        └→  JwtProvider  (global/security/jwt)
                             - createAccessToken(memberId, role)
                             - parse(token)
```

- `JwtProvider`는 특정 도메인의 기능이 아니다. 미션 6의 인증 필터도 쓰기 때문에 `global/security/jwt`에 뒀다.
- `createAccessToken`은 `Member` 대신 `(Long memberId, Role role)`을 받는다. 엔티티에 의존하지 않으므로 테스트에서 `Member`를 만들 필요가 없다. `id`는 DB가 채우는 값이라 테스트에서 만들기 번거롭다.
- 아쉬운 점: `global`에 있는 `JwtProvider`가 `domain.member.entity.Role`을 import한다. 원래 `global`은 특정 도메인을 몰라야 한다. 지금 규모에서는 그대로 둔다.

## 2. 의존성

```groovy
ext {
    JJWT_VERSION = "0.12.3"
}

implementation "io.jsonwebtoken:jjwt-api:${JJWT_VERSION}"
runtimeOnly "io.jsonwebtoken:jjwt-gson:${JJWT_VERSION}"
runtimeOnly "io.jsonwebtoken:jjwt-impl:${JJWT_VERSION}"
```

| jar | 범위 | 이유 |
|---|---|---|
| `jjwt-api` | `implementation` | 내 코드가 직접 쓰는 `Jwts`, `Keys`, `Claims`가 여기 있다 |
| `jjwt-impl` | `runtimeOnly` | 실제 구현이다. 컴파일할 때 보이지 않으니 실수로 `impl` 패키지를 import할 수 없다 |
| `jjwt-gson` | `runtimeOnly` | JWT 헤더와 페이로드를 JSON으로 직렬화한다 |

- **Spring Boot가 jjwt 버전을 관리하지 않는다.** 그래서 버전을 직접 적었다.
- 0.12에서 API가 많이 바뀌었다. 블로그에 많은 0.11 이하 예제(`setSubject()`, `parseClaimsJws()`)는 deprecated다. 0.12에서는 `subject()`, `parseSignedClaims()`를 쓴다.
- JSON 라이브러리로 jackson 대신 gson을 골랐다. Spring Boot 4는 기본이 Jackson 3(`tools.jackson`)이고, `jjwt-jackson`은 Jackson 2(`com.fasterxml`)를 쓴다. 다만 확인해 보니 springdoc이 이미 Jackson 2를 쓰고 있어서, jackson을 골랐어도 새로 추가되는 의존성은 없었다.

```bash
./gradlew dependencyInsight --dependency jackson-databind --configuration runtimeClasspath
```

## 3. secret 관리: `.env`

```yaml
# application-local.yml
spring:
  config:
    import: optional:file:.env[.properties]

jwt:
  secret: ${JWT_SECRET}
  access-token:
    expiration: 3600000
```

```properties
# .env (git 제외)
JWT_SECRET=<openssl rand -base64 48>
```

- **Spring Boot는 `.env`를 자동으로 읽지 않는다.** `${JWT_SECRET}`는 OS 환경 변수와 시스템 프로퍼티에서 값을 찾는다. `spring.config.import`로 `.env`를 properties 파일처럼 읽게 했다.
- `optional:`은 ".env 파일이 없어도 에러 내지 마라"는 뜻이다. 파일이 없으면 `${JWT_SECRET}`은 여전히 채워지지 않는다.
- `.env`는 실행할 때의 **작업 디렉터리** 기준으로 찾는다. IntelliJ와 `./gradlew bootRun`은 프로젝트 루트에서 실행되므로 문제없다.
- `${JWT_SECRET}`에는 **기본값을 두지 않았다.** 값이 없으면 앱이 시작하지 않는다(`Could not resolve placeholder 'JWT_SECRET'`). secret 없이 실행되는 것보다 시작할 때 실패하는 게 안전하다.
- `jwt`는 `spring:` 밖(최상위)에 뒀다. `spring.*`는 Spring Boot가 쓰는 네임스페이스다. 처음에는 `spring:` 아래에 넣어서 `spring.jwt.secret`이 됐었다.
- 필요한 키는 `.env.example`에 값 없이 적어 커밋했다. clone한 사람이 무엇을 채워야 하는지 알 수 있다.
- test 프로필(`application-test.yml`)에는 테스트 전용 secret을 직접 적었다. 그래서 테스트는 `.env` 없이 돌아간다.

## 4. 키 만들기

```java
this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
```

- `.env`의 secret은 `openssl rand -base64`로 만든 Base64 문자열이다. 디코딩해서 바이트로 쓴다.
- 처음에는 JDK 클래스로 만들었다: `new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256")`

| | `new SecretKeySpec(...)` | `Keys.hmacShaKeyFor(...)` |
|---|---|---|
| 키 길이 검사 | 안 한다. 10바이트 키도 만들어진다 | 256bit 미만이면 `WeakKeyException` |
| 언제 실패하나 | 처음 서명할 때(첫 로그인) | **앱이 시작할 때** |

- secret이 없을 때와 같은 이유로, 키가 약한 것도 시작할 때 바로 드러나는 쪽을 골랐다.
- 키는 생성자에서 **한 번만** 만든다.
- 참고: 끝에 `=` 패딩이 없는 Base64도 jjwt 디코더는 받아준다(테스트 secret으로 확인).

## 5. 토큰 생성

```java
public String createAccessToken(Long memberId, Role role) {
    Date now = new Date();
    Date expirationTime = new Date(now.getTime() + accessTokenExpiration);
    return Jwts.builder()
            .subject(memberId.toString())
            .claim("role", role.name())
            .issuedAt(now)
            .expiration(expirationTime)
            .signWith(key, Jwts.SIG.HS256)
            .compact();
}
```

| claim | 값 | 이유 |
|---|---|---|
| `sub` | 회원 id (`"1"`) | 이메일은 바뀔 수 있다. id는 안 바뀐다. `subject()`는 String만 받는다 |
| `role` | `"USER"` | enum을 그대로 넣으면 JSON 라이브러리가 어떻게 직렬화하느냐에 달려 있다. `role.name()`으로 문자열을 넣고, 꺼낼 때 `Role.valueOf()`로 되돌린다 |
| `iat`, `exp` | 발급, 만료 시각 | `issuedAt`과 `expiration`에 **같은 `now`**를 써서 `exp - iat`가 정확히 설정값이 된다 |

### 키 길이에 따라 알고리즘이 바뀐다

`signWith(key)`처럼 알고리즘을 안 적으면 jjwt가 **키 길이에 맞는 가장 강한 HMAC**을 고른다.

| 키 길이 | 자동 선택 |
|---|---|
| 32~47바이트 | HS256 |
| 48~63바이트 | HS384 |
| 64바이트 이상 | HS512 |

`.env`의 secret은 `openssl rand -base64 48`로 만들어서 디코딩하면 48바이트다. 알고리즘을 안 적었으면 HS384가 됐을 것이다. `Jwts.SIG.HS256`을 직접 적었다.

### 단위 주의: ms와 초

- `Date.getTime()`과 yml의 `expiration`은 **ms**다.
- JWT의 `iat`, `exp`와 응답의 `expiresIn`은 **초**다.
- 토큰의 만료 시각 계산에는 ms 필드를 쓰고, 초 단위 값은 응답의 `expiresIn`에만 쓴다.

## 6. 로그인 응답: `TokenResponse`

```java
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, BEARER, expiresIn);
    }
}
```

```java
String accessToken = jwtProvider.createAccessToken(member.getId(), member.getRole());
return TokenResponse.of(accessToken, jwtProvider.getAccessTokenExpirationSeconds());
```

- `tokenType`은 항상 `"Bearer"`다. 호출하는 쪽에서 매번 넘기면 오타가 날 수 있어서 정적 팩토리 안에서 고정했다.
- `expiresIn`은 OAuth2 관례대로 **초 단위**다. ms를 초로 바꾸는 일은 `JwtProvider`가 한다(`getAccessTokenExpirationSeconds()`). 메서드 이름에 `Seconds`를 붙여서 단위를 드러냈다.
- 회원가입 응답은 그대로 `AuthResponse(email, name)`를 쓴다. 미션 4에서 예정한 대로 가입 응답과 로그인 응답 DTO를 나눴다.
- 한계: record의 기본 생성자는 항상 public이다. 그래서 `new TokenResponse(token, "bearer", 3600)`처럼 팩토리를 거치지 않고 만드는 것도 가능하다. 응답 DTO에서는 팩토리를 제공하는 정도로 충분하다고 봤다.

```json
{"accessToken": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 3600}
```

## 7. 토큰 검증 / 파싱

```java
private final JwtParser parser;

public JwtProvider(...) {
    this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    ...
    this.parser = Jwts.parser().verifyWith(key).build();
}

public Claims parse(String token) {
    return parser.parseSignedClaims(token).getPayload();
}
```

- `parseSignedClaims`는 **서명된 토큰만** 받는다. 서명이 없는 토큰(`alg: none`)은 거부한다.
- 처음에는 `parse`가 불릴 때마다 `Jwts.parser()...build()`를 했다. 준비 작업에 쓰는 값은 `key` 하나이고 앱이 실행되는 동안 바뀌지 않는다. 그래서 생성자에서 한 번만 만들게 바꿨다. `Pattern.compile()`을 상수로 두는 것과 같은 이유다.
- `JwtParser`는 불변 객체라서 여러 요청(스레드)이 하나를 같이 써도 안전하다. `JwtProvider`는 싱글톤 빈이므로 이 점이 중요하다.

### 예외를 바꾸지 않고 그대로 던진다

| 경우 | 예외 |
|---|---|
| 만료 | `io.jsonwebtoken.ExpiredJwtException` |
| 다른 키로 서명 (위조) | `io.jsonwebtoken.security.SignatureException` |
| 형식 오류 (`"abc"`) | `io.jsonwebtoken.MalformedJwtException` |
| `null`, `""` | `IllegalArgumentException` (`JwtException`의 하위 클래스가 아니다) |

- 앞의 세 가지는 모두 `JwtException`의 하위 클래스다.
- `boolean validate()`로 바꾸면 실패 이유를 구분할 수 없다. 예외를 그대로 던지면 테스트에서 이유별로 확인할 수 있다.
- `BusinessException`으로 바꾸지 않았다. 미션 6의 JWT 필터는 **DispatcherServlet보다 앞에서** 실행된다. 그래서 필터에서 던진 예외는 `GlobalExceptionHandler`(`@RestControllerAdvice`)가 잡지 못한다. 401 JSON 응답은 미션 6에서 필터나 `AuthenticationEntryPoint`가 따로 만들어야 한다.
- 반환 타입은 `Claims`로 했다. 그러면 미션 6의 필터에서 `Long.valueOf(claims.getSubject())`, `Role.valueOf(...)` 변환이 필요하고, 필터도 jjwt를 알게 된다. 불편하면 `(Long memberId, Role role)` record로 바꾼다.

## 8. 테스트: `JwtProviderTest`

```java
class JwtProviderTest {

    private static final String SECRET = "...";
    private static final String OTHER_SECRET = "...";
    private final JwtProvider jwtProvider = new JwtProvider(SECRET, 3_600_000);

    @Test
    void 만료된_토큰은_ExpiredJwtException() {
        JwtProvider expiredProvider = new JwtProvider(SECRET, -1_000);
        String accessToken = expiredProvider.createAccessToken(1L, Role.USER);
        assertThatThrownBy(() -> expiredProvider.parse(accessToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 다른_키로_서명된_토큰은_SignatureException() {
        JwtProvider otherJwtProvider = new JwtProvider(OTHER_SECRET, 3_600_000);
        String otherAccessToken = otherJwtProvider.createAccessToken(1L, Role.USER);
        assertThatThrownBy(() -> jwtProvider.parse(otherAccessToken))
                .isInstanceOf(SignatureException.class);
    }
    ...
}
```

- **Spring 없이** `new JwtProvider(...)`로 만든다. Docker도 필요 없고 바로 끝난다. 이렇게 하려고 `@Value`를 필드가 아니라 **생성자 파라미터**에 붙였다. 필드에 붙이면 Spring이 주입하기 전까지 값이 비어 있다.
- **만료**: 만료시간을 음수로 줘서 이미 만료된 토큰을 만든다. `Thread.sleep`이 필요 없다.
- **다른 키**: 다른 secret으로 **만들고**, 우리 secret으로 **검증한다**. 처음에는 같은 Provider로 만들고 검증해서 예외가 안 났다. [트러블슈팅 5](troubleshooting.md) 참고.
- **형식 오류**: 토큰 모양이 아닌 문자열 `"abc"`.

### 아직 없는 테스트

- `exp - iat`가 설정값과 같은지 확인하는 테스트. ms와 초가 섞이는 실수를 잡아준다.
- `parse(null)` → `IllegalArgumentException`. 미션 6에서 Authorization 헤더가 없을 때 생기는 경우다.

## 9. 완료 조건 확인

| 확인 | 결과 |
|---|---|
| 로그인 응답 | `{accessToken, tokenType: "Bearer", expiresIn: 3600}` |
| jwt.io Header | `alg: HS256` |
| jwt.io Payload | `sub`: 회원 id, `role`: `"USER"`, `exp - iat` = 3600 |
| `JwtProviderTest` | 4개 통과 |
| `./gradlew test` 전체 | 18개 통과 |

- jwt.io에는 **토큰만** 넣고 secret은 넣지 않았다. 서명 검증 칸이 "Invalid Signature"여도 정상이다.
- JWT의 헤더와 페이로드는 Base64URL로 인코딩된 JSON일 뿐이고 **암호화되지 않는다.** 누구나 읽을 수 있으므로 비밀번호 같은 민감한 정보는 넣지 않는다.

- [x] jwt.io에서 `sub`, `role` 확인
- [x] `JwtProviderTest` 정상 / 만료 / 다른 키 / 형식 오류 통과

## 10. 미션 6으로 넘기는 것

- `SecurityConfig`의 `anyRequest().permitAll()`을 바꿔야 한다(TODO). 지금은 토큰이 없어도 모든 API에 접근된다.
- 필터에서 `Authorization: Bearer <token>` 헤더를 꺼내서 `parse`로 검증한다. 헤더가 없거나 `Bearer `로 시작하지 않는 경우도 처리해야 한다.
- 검증에 실패하면 401 JSON을 응답한다. `GlobalExceptionHandler`는 필터 예외를 잡지 못한다(7번 참고).
- yml의 `jwt.refresh-token.expiration`은 아직 쓰는 곳이 없다.
