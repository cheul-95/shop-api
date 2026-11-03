# 05. 회원가입 / 로그인 API

> 커밋: `1358571 feat: 미션4 회원가입/로그인 API (BCrypt, 중복 이메일 409, 로그인 실패 401)`

## 미션 요구사항

- `POST /api/auth/signup`: 회원가입, 비밀번호는 BCrypt로 저장
- `POST /api/auth/login`: 로그인 (토큰 발급은 미션 5)
- 중복 이메일이면 409
- **완료 조건**: 가입 201 → 중복 가입 409 → 틀린 비밀번호 401

---

## 1. 계층 구조

```
AuthController  →  AuthService  →  MemberRepository
 (요청 검증, 상태 코드)   (중복 검사, 인코딩, 검증)    (조회/저장)
```

- 패키지는 `domain/auth`로 따로 뒀다. 회원 엔티티와 Repository는 `domain/member`에 있고, auth는 이걸 가져다 쓰는 쪽이다.
- 실패는 서비스에서 `BusinessException`을 던지기만 한다. 409/401 응답은 미션 1의 `GlobalExceptionHandler`가 만든다.

## 2. 요청 DTO와 Bean Validation

```java
public record SignupRequest(@Email @NotBlank @Size(max = 100) String email,
                            @NotBlank @Size(min = 8, max = 64) String password,
                            @NotBlank @Size(max = 50) String name) {
}

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
```

```java
@PostMapping("/signup")
public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
    AuthResponse response = authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

- `@Valid`가 있어야 검증이 실행된다. 실패하면 `MethodArgumentNotValidException` → 400 `INVALID_INPUT`.
- `@Size(max)`는 `Member` 엔티티의 컬럼 길이(`email` 100, `name` 50)에 맞췄다. 검증을 통과했는데 DB에서 잘리거나 에러 나는 일이 없게 한다.
- `password`의 `max = 64`는 **평문 기준**이다. DB 컬럼(`length = 100`)에는 인코딩된 60자 BCrypt 해시가 들어간다.
  - BCrypt는 입력의 앞 72바이트까지만 쓴다. 그보다 긴 비밀번호는 뒷부분이 무시되므로 상한을 두는 게 맞다.
- `LoginRequest`에는 형식 검증(`@Email`, `@Size`)을 넣지 않았다. 로그인은 "맞는지"만 확인하면 되고, 형식이 틀리면 어차피 일치하는 회원이 없다.
- 가입은 201 Created, 로그인은 200 OK. 새 리소스가 생기는지가 기준이다.

## 3. 비밀번호 저장: `PasswordEncoder`

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

```java
String encodedPassword = passwordEncoder.encode(request.password());
Member member = new Member(request.email(), encodedPassword, request.name());
```

```java
if (!passwordEncoder.matches(request.password(), member.getPassword())) {
    throw new BusinessException(ErrorCode.FAIL_LOGIN);
}
```

DB에는 이렇게 저장된다.

```
$2a$10$J6OVvPQXQ0O/lGIyqD.luuEi9NvOlFKmUIvIOzSjuqHC4ZKirxFXK
 │   │  └ salt(22자) + 해시(31자)
 │   └ cost: 2^10번 반복
 └ 알고리즘 버전
```

- **같은 비밀번호도 매번 다른 해시가 나온다.** salt가 랜덤이라서다. 그래서 `encode(입력) == 저장값`으로 비교할 수 없고 `matches()`를 써야 한다. `matches()`는 저장값에서 salt를 꺼내 입력을 같은 방식으로 해시한 뒤 비교한다.
- 복호화는 안 된다(단방향). 비밀번호를 잊은 사용자에게는 "찾기"가 아니라 "재설정"을 제공해야 한다.
- 일부러 느리다. cost를 올리면 무차별 대입 공격 비용이 같이 올라간다.
- `PasswordEncoder` 인터페이스로 주입받아서, 나중에 Argon2 등으로 바꿔도 서비스 코드는 그대로다.
- `Member` 생성자 파라미터 이름을 `encodedPassword`로 둬서, 인코딩된 값을 넣어야 한다는 걸 호출하는 쪽에 드러냈다.

## 4. 중복 이메일 → 409

```java
@Transactional
public AuthResponse signup(SignupRequest request) {
    if (memberRepository.existsByEmail(request.email())) {
        throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
    }
    ...
}
```

```java
DUPLICATE_EMAIL(HttpStatus.CONFLICT, "중복된 이메일 입니다."),
```

- `existsByEmail`은 엔티티 전체가 아니라 존재 여부만 확인한다(`select ... limit 1`).
- 409 Conflict: 요청 형식은 맞지만 **현재 서버 상태와 충돌**해서 처리할 수 없다는 뜻이다. 형식이 틀린 400과 구분한다.
- 클래스에는 `@Transactional(readOnly = true)`, 저장하는 `signup`에만 `@Transactional`. 미션 3에서 정한 방식 그대로다.

### 남은 구멍: 동시 가입

같은 이메일로 요청 두 개가 동시에 들어오면 둘 다 `existsByEmail` 검사를 통과할 수 있다.

```
요청 A: existsByEmail → false
요청 B: existsByEmail → false
요청 A: INSERT 성공
요청 B: INSERT → unique 제약 위반 → DataIntegrityViolationException → 500
```

- 데이터는 `Member.email`의 `unique = true` 제약 덕분에 DB가 막아준다. 다만 응답이 409가 아니라 500이 된다.
- 해결하려면 `DataIntegrityViolationException`을 잡아서 `DUPLICATE_EMAIL`로 바꾼다. 이번 미션에서는 하지 않았다.
- **애플리케이션 검사는 친절한 에러 메시지용이고, 최종 방어선은 DB 제약이다.**

## 5. 로그인 실패 → 401, 이유는 숨긴다

```java
public AuthResponse login(LoginRequest request) {
    Member member = memberRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.FAIL_LOGIN));

    if (!passwordEncoder.matches(request.password(), member.getPassword())) {
        throw new BusinessException(ErrorCode.FAIL_LOGIN);
    }
    return AuthResponse.from(member);
}
```

- "이메일이 없음"과 "비밀번호가 틀림"을 **같은 에러 코드**로 응답한다.
- 둘을 나누면 응답만 보고 어떤 이메일이 가입돼 있는지 알아낼 수 있다(계정 열거, user enumeration).
- 401 Unauthorized: 인증 실패. 이름과 달리 "인증(누구인지)"이 안 됐다는 뜻이다. 인증은 됐는데 권한이 없는 경우는 403이다(미션 6).
- 참고: 응답 시간 차이로도 구분이 될 수 있다. 이메일이 없으면 BCrypt 비교를 건너뛰어서 응답이 더 빠르다. 실무에서는 없는 경우에도 더미 해시와 비교해서 시간을 맞추기도 한다.

## 6. 응답 DTO: `AuthResponse`

```java
public record AuthResponse(String email, String name) {
    public static AuthResponse from(Member member) {
        return new AuthResponse(member.getEmail(), member.getName());
    }
}
```

- 처음에는 `password`도 들어 있어서 **BCrypt 해시가 응답으로 나갔다.** 리뷰에서 발견해서 뺐다. [트러블슈팅 4](troubleshooting.md) 참고.
- 미션 3에서 "엔티티를 그대로 응답하지 않는 이유"로 `Member.password`를 예로 들었는데, DTO를 써도 필드를 그대로 옮기면 똑같이 노출된다. **DTO에 뭘 담을지는 응답 기준으로 고른다.**
- 로그인 응답은 미션 5에서 `{accessToken, tokenType, expiresIn}`으로 바뀐다. 그때 가입/로그인 응답 DTO를 나눌 예정이다.

## 7. 완료 조건 확인

로컬에서 앱을 띄우고 curl로 확인했다.

| 요청 | 결과 | 응답 |
|---|---|---|
| 가입 | **201** | `{"email":"...","name":"tester"}` |
| 같은 이메일로 다시 가입 | **409** | `{"code":"DUPLICATE_EMAIL", ...}` |
| 틀린 비밀번호로 로그인 | **401** | `{"code":"FAIL_LOGIN", ...}` |
| 없는 이메일로 로그인 | 401 | 위와 같은 응답 |
| 올바른 로그인 | 200 | `{"email":"...","name":"tester"}` |
| 잘못된 입력 (이메일 형식, 짧은 비밀번호, 빈 이름) | 400 | `{"code":"INVALID_INPUT", ...}` |

- [x] 가입 201 → 중복 409 → 틀린 비밀번호 401
- [x] DB `member.password`가 `$2a$10$...` 형태로 저장됨, `role`은 `USER`
- [x] 응답에 비밀번호 없음
