# 학습 기록

Spring Boot / JPA를 미션 단위로 공부하면서 정리한 문서입니다.
각 문서는 "무엇을 만들었는지"보다 **"왜 이렇게 했는지"** 위주로 정리했습니다.

| 문서 | 내용 | 관련 커밋 |
|---|---|---|
| [00. 프로젝트 초기 세팅](00-project-setup.md) | 의존성, Docker MySQL, 프로필 분리, JPA/Security/Swagger 설정, Testcontainers | `5e3c8b7` |
| [01. 공통 코드](01-common-code.md) | BaseTimeEntity, ErrorCode, BusinessException, 전역 예외 처리 | `916e8ea` |
| [02. 엔티티 설계](02-entity-design.md) | Member, Category, Product, Order, OrderItem과 연관관계 매핑 | `73459c9`, `9ff154f` |
| [03. 테스트](03-testing.md) | 단위 테스트, AssertJ, 예외 검증, 슬라이스/통합 테스트 | 전체 |
| [04. 상품 조회 API](04-product-api.md) | DTO 응답, 페이징 검색, N+1과 `@EntityGraph`, 기본 정렬, data.sql | (커밋 후 추가) |
| [트러블슈팅](troubleshooting.md) | 실제로 겪은 오류와 버그, 원인과 해결 | - |

## 미션 로드맵

| 미션 | 내용 | 완료 조건 | 상태 |
|---|---|---|---|
| 0. 프로젝트 생성 | 의존성, docker-compose(MySQL 8.4), application.yml | 앱 실행 시 `Started ...Application` | ✅ 완료 |
| 1. 공통 코드 | BaseTimeEntity + Auditing, ErrorCode, BusinessException, GlobalExceptionHandler | 없는 상품 조회 시 404 `{"code":"PRODUCT_NOT_FOUND", ...}` (미션 3에서 확인) | ✅ 완료 (미션 3에서 404 `PRODUCT_NOT_FOUND` 확인, 에러 코드는 enum 이름으로 통일) |
| 2. 엔티티 | Member, Category, Product, Order, OrderItem | SQL 로그에 테이블 5개 CREATE, `ProductTest` 통과 | ✅ 완료 |
| 3. 상품 조회 API | `GET /api/products?keyword=&page=&size=`, `GET /api/products/{id}`, DTO 응답, data.sql | 목록 조회 시 SELECT 2번(목록 + 개수)만, 카테고리 N+1 없음 | ✅ SQL 로그 확인 완료 (`@EntityGraph`, 목록 + count 2번) |
| 4. 회원가입 / 로그인 | `POST /api/auth/signup`, `POST /api/auth/login`, BCrypt, 중복 409 | 가입 201 → 중복 가입 409 → 틀린 비밀번호 401 | ⬜ |
| 5. JWT 발급 | jjwt, secret/만료시간은 yml, `{accessToken, tokenType, expiresIn}` | jwt.io에서 sub, role 확인 / `JwtProviderTest`(정상/만료/다른 키/형식 오류) 통과 | ⬜ |
| 6. JWT 인증 필터 | Bearer 토큰 검증, 경로별 허용/인증, `GET /api/members/me`, 401/403 JSON | 토큰 없이 /me → 401 / 토큰과 함께 → 200 / 상품 조회는 토큰 없이 200 | ⬜ |
| 🎁 보너스 | `POST /api/orders` 주문 생성 + 재고 차감, 재고 부족 409 | DB 재고 감소 확인, `@Transactional` 제거 시 재고가 안 줄어드는 것 확인 | ⬜ |

## 진행 방식

- 미션이 끝날 때마다 리뷰를 받는다.
- 막히면 단계적으로 도움을 요청한다: **키워드만 → 힌트만 → 이 부분만 코드**
- 에러가 나면 메시지 전체를 붙여넣고, 원인을 찾는 방법부터 같이 본다.
