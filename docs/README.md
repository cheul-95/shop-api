# 학습 기록

Spring Boot / JPA를 미션 단위로 공부하면서 정리한 문서입니다.
각 문서는 "무엇을 만들었는지"보다 **"왜 이렇게 했는지"** 위주로 정리했습니다.

| 문서 | 내용 | 관련 커밋 |
|---|---|---|
| [00. 프로젝트 초기 세팅](00-project-setup.md) | 의존성, Docker MySQL, 프로필 분리, JPA/Security/Swagger 설정, `default_batch_fetch_size`를 100으로 둔 이유, Testcontainers | `5e3c8b7` |
| [01. 공통 코드](01-common-code.md) | BaseTimeEntity, ErrorCode, BusinessException(일반 `RuntimeException`과의 차이), 전역 예외 처리, record/interface/class/enum 선택 기준 | `916e8ea` |
| [02. 엔티티 설계](02-entity-design.md) | Member, Category, Product, Order, OrderItem과 연관관계 매핑 | `73459c9`, `9ff154f` |
| [03. 테스트](03-testing.md) | 단위 테스트, AssertJ, 예외 검증, 슬라이스/통합 테스트 | 전체 |
| [04. 상품 조회 API](04-product-api.md) | DTO 응답, 페이징 검색, N+1과 `@EntityGraph`, 기본 정렬, data.sql | `b4a086c` |
| [05. 회원가입 / 로그인 API](05-auth-api.md) | Bean Validation, BCrypt `PasswordEncoder`, 중복 409, 로그인 실패 401과 계정 열거 방지 | `1358571` |
| [06. JWT 발급](06-jwt.md) | jjwt 의존성, `.env`로 secret 관리, 키 길이와 알고리즘, 토큰 생성/검증, `TokenResponse`, `JwtProviderTest` | `ebf7be6`, `7cc36ca` |
| [07. JWT 인증 필터](07-jwt-filter.md) | `OncePerRequestFilter`, SecurityContext, 경로별 인가 규칙, 401/403 JSON(EntryPoint, AccessDeniedHandler), `@AuthenticationPrincipal`, `GET /api/members/me` | `0c9264d` |
| [08. 주문 생성 API](08-order.md) | `POST /api/orders`, `OrderItem.create`로 재고 차감 강제, 409, `@Transactional` 제거 실험과 dirty checking, OSIV, 통합 테스트에서 `@Transactional`을 빼는 이유, 롤백 테스트의 한계 | `35f0694`, `35c446b` |
| [09. 재고 동시성](09-concurrency.md) | 동시 주문 100건 재현 테스트(`ExecutorService`, `CountDownLatch`), Lost Update, FK S 락으로 인한 데드락, 낙관적 락 vs 비관적 락 비교, 락 순서(`order by id`) | `f432a7b`, `3b568e1` |
| [10. AWS 배포와 CI/CD](10-deploy.md) | EC2 + Docker Compose 구성 이유, multi-stage Dockerfile, prod 프로필(fail-fast, `validate`), `depends_on: service_healthy`, GitHub Actions → GHCR → SSH 배포, 보안그룹 | `4b42fc2`, `fadf6ac`, `99bf182` |
| [트러블슈팅](troubleshooting.md) | 실제로 겪은 오류와 버그, 원인과 해결 | - |
| [에러 코드와 HTTP 상태](error-codes.md) | 전체 에러 코드 목록, 400/401/403/404/409/500 선택 기준, 예외가 응답이 되는 경로 | - |

작업 규칙(브랜치, 커밋 메시지, PR): [CONTRIBUTING.md](../CONTRIBUTING.md)

## 미션 로드맵

| 미션 | 내용 | 완료 조건 | 상태 |
|---|---|---|---|
| 0. 프로젝트 생성 | 의존성, docker-compose(MySQL 8.4), application.yml | 앱 실행 시 `Started ...Application` | ✅ 완료 |
| 1. 공통 코드 | BaseTimeEntity + Auditing, ErrorCode, BusinessException, GlobalExceptionHandler | 없는 상품 조회 시 404 `{"code":"PRODUCT_NOT_FOUND", ...}` (미션 3에서 확인) | ✅ 완료 (미션 3에서 404 `PRODUCT_NOT_FOUND` 확인, 에러 코드는 enum 이름으로 통일) |
| 2. 엔티티 | Member, Category, Product, Order, OrderItem | SQL 로그에 테이블 5개 CREATE, `ProductTest` 통과 | ✅ 완료 |
| 3. 상품 조회 API | `GET /api/products?keyword=&page=&size=`, `GET /api/products/{id}`, DTO 응답, data.sql | 목록 조회 시 SELECT 2번(목록 + 개수)만, 카테고리 N+1 없음 | ✅ SQL 로그 확인 완료 (`@EntityGraph`, 목록 + count 2번) |
| 4. 회원가입 / 로그인 | `POST /api/auth/signup`, `POST /api/auth/login`, BCrypt, 중복 409 | 가입 201 → 중복 가입 409 → 틀린 비밀번호 401 | ✅ 완료 (curl로 201 / 409 / 401 확인, BCrypt 저장 확인) |
| 5. JWT 발급 | jjwt, secret/만료시간은 yml, `{accessToken, tokenType, expiresIn}` | jwt.io에서 sub, role 확인 / `JwtProviderTest`(정상/만료/다른 키/형식 오류) 통과 | ✅ 완료 (jwt.io에서 HS256, sub, role 확인, `JwtProviderTest` 4개 통과) |
| 6. JWT 인증 필터 | Bearer 토큰 검증, 경로별 허용/인증, `GET /api/members/me`, 401/403 JSON | 토큰 없이 /me → 401 / 토큰과 함께 → 200 / 상품 조회는 토큰 없이 200 | ✅ 완료 (curl로 401 / 200 / 상품 200 확인, 위조 토큰 401, 전체 테스트 18개 통과. 403은 임시 ADMIN 경로 + USER 토큰으로 확인 후 되돌림) |
| 🎁 보너스 | `POST /api/orders` 주문 생성 + 재고 차감, 재고 부족 409 | DB 재고 감소 확인, `@Transactional` 제거 시 재고가 안 줄어드는 것 확인 | ✅ 완료 (curl로 주문/409/401/400 확인, DB 재고 10→8·100→99, `@Transactional` 제거 시 주문만 저장되고 재고 그대로, `OrderServiceTest` 5개 포함 전체 23개 통과) |
| 🚀 AWS 배포 | EC2 + Docker Compose, GitHub Actions CI/CD (test → GHCR → SSH 배포) | 외부에서 Swagger 접속, main 머지 시 자동 배포 | ✅ 완료 (PR #14, 첫 main 실행에서 test/build/deploy 성공, 헬스체크 통과) |
