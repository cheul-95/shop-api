# 00. 프로젝트 초기 세팅

> 커밋: `5e3c8b7 chore: 미션0 프로젝트 초기 세팅`

## 미션 요구사항

- start.spring.io에서 생성: Gradle, Java 25, Spring Boot 4.0.x
- 의존성: Web MVC, Validation, Data JPA, MySQL Driver, Security, Lombok, springdoc
- docker-compose.yml: MySQL 8.4 (utf8mb4, 포트 충돌 주의)
- application.yml: DB 연결, ddl-auto, open-in-view, batch_fetch_size, SQL 로그
- **완료 조건**: 앱을 실행하면 에러 없이 뜨고, 로그에 `Started ...Application`이 보인다.

> 미션 안내와 실제 프로젝트의 차이: 미션은 Java 25 / Boot 4.0.x 기준이지만, 이 프로젝트는 **Java 17 / Boot 4.1**로 만들었다. 미션 범위의 기능에는 차이가 없다.

> Security를 넣으면 모든 URL이 막히고 콘솔에 임시 비밀번호(`Using generated security password: ...`)가 출력된다. 정상 동작이다. 이 프로젝트는 `SecurityConfig`를 바로 만들어서 전체 허용으로 열어뒀다. ([5. Spring Security 기본 설정](#5-spring-security-기본-설정))

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 언어 | Java 17 |
| 프레임워크 | Spring Boot 4.1 |
| 빌드 | Gradle (Wrapper 포함) |
| DB | MySQL 8.4 (Docker) |
| ORM | Spring Data JPA (Hibernate) |
| 보안 | Spring Security |
| 검증 | Bean Validation |
| API 문서 | springdoc-openapi (Swagger UI) |
| 테스트 | JUnit 5, AssertJ, Testcontainers |
| 기타 | Lombok |

## 1. build.gradle 의존성

### 의존성 범위(scope)

| 범위 | 의미 | 예시 |
|---|---|---|
| `implementation` | 컴파일 + 실행 모두 필요 | JPA, Security, Web |
| `compileOnly` | 컴파일할 때만 필요, jar에 포함 안 됨 | Lombok (컴파일 시 코드를 생성하고 끝) |
| `annotationProcessor` | 컴파일 중 어노테이션을 처리 | Lombok |
| `runtimeOnly` | 실행할 때만 필요 | MySQL 드라이버 (코드에서 직접 import하지 않음) |
| `testImplementation` | `src/test`에서만 사용, 배포 jar에 포함 안 됨 | JUnit, AssertJ, Testcontainers |

- PHP Composer로 비유하면 `require`가 `implementation`, `require-dev`가 `testImplementation`에 해당한다.
- Spring Boot 4부터는 테스트 스타터가 기능별로 나뉘었다 (`spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test` 등).

### 버전을 안 적어도 되는 이유

`io.spring.dependency-management` 플러그인이 Spring Boot 버전에 맞는 라이브러리 버전을 정해준다.
Spring Boot가 관리하지 않는 라이브러리(springdoc 등)만 버전을 직접 적는다.

## 2. Docker로 로컬 MySQL 띄우기

`docker-compose.yml`

```yaml
services:
    mysql:
      image: mysql:8.4
      ports:
        - "127.0.0.1:3308:3306"   # 내 PC의 3308 → 컨테이너 3306
      environment:
        MYSQL_DATABASE: shop
        MYSQL_USER: shop
        MYSQL_PASSWORD: shop
        TZ: Asia/Seoul
      command:
        - --character-set-server=utf8mb4
        - --collation-server=utf8mb4_unicode_ci
      volumes:
        - mysql-data:/var/lib/mysql
```

| 설정 | 이유 |
|---|---|
| `3308:3306` | PC에 이미 MySQL/MariaDB(3306)가 깔려 있어도 포트가 충돌하지 않게 |
| `127.0.0.1:` | 내 PC에서만 접속 가능. 안 붙이면 같은 네트워크(카페 와이파이 등)의 다른 기기도 `내IP:3308`로 접속할 수 있는데, 비밀번호가 저장소에 공개돼 있어서 위험 |
| `utf8mb4` | 한글, 이모지까지 저장 가능. MySQL의 `utf8`은 3바이트까지라 이모지가 깨짐 |
| `TZ: Asia/Seoul` | DB 시간대를 한국 시간으로 |
| `volumes` | 컨테이너를 지워도 데이터가 남도록 |
| `healthcheck` | DB가 실제로 접속 가능한 상태인지 주기적으로 확인 |

```bash
docker compose up -d    # 실행
docker compose down     # 종료 (볼륨은 유지)
```

## 3. 프로필 분리

설정 파일을 환경별로 나눴다.

| 파일 | 용도 |
|---|---|
| `application.yml` | 모든 환경 공통 설정 |
| `application-local.yml` | 로컬 개발 (기본 프로필) |
| `src/test/resources/application-test.yml` | 테스트 |

- `application.yml`의 `spring.profiles.default: local` → 프로필을 따로 지정하지 않으면 `local`로 실행된다.
- 테스트에서는 `@ActiveProfiles("test")`로 test 프로필을 켠다.
- 공통 설정 위에 프로필 설정이 덮어씌워지는 구조다.

### 환경변수 + 기본값

```yaml
url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3308}/${DB_NAME:shop}...
username: ${DB_USERNAME:shop}
password: ${DB_PASSWORD:shop}
```

- `${환경변수:기본값}` 문법. 환경변수가 있으면 그 값을, 없으면 `:` 뒤의 값을 쓴다.
- 기본값은 docker-compose의 개발용 계정이라 저장소에 올라가도 괜찮다. clone 받은 사람이 `docker compose up`만 하면 바로 실행된다.
- **운영 서버 비밀번호는 기본값으로도 yml에 적지 않는다.** 환경변수나 `.env`로만 넣는다. (`.env`는 `.gitignore`에 등록됨)

## 4. JPA 설정

```yaml
spring:
  jpa:
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        highlight_sql: true
        default_batch_fetch_size: 100
```

### open-in-view: false (OSIV 끄기)

- OSIV가 켜져 있으면 요청이 끝날 때(뷰 렌더링까지)까지 DB 커넥션을 붙잡고 있다.
- 끄면 커넥션은 **트랜잭션 범위 안에서만** 쓰고 바로 반납한다 → 트래픽이 많을 때 커넥션 풀이 고갈되지 않는다.
- 대신 지연로딩(LAZY)은 서비스 계층의 `@Transactional` 안에서 끝내야 한다. 트랜잭션 밖(컨트롤러)에서 LAZY 필드를 건드리면 `LazyInitializationException`이 난다.

### default_batch_fetch_size: 100

- 연관된 엔티티를 지연로딩할 때 하나씩 쿼리를 날리지 않고 `IN (?, ?, ...)`으로 최대 100개씩 묶어서 조회한다.
- N+1 문제를 완화하는 가장 간단한 방법이다.

### SQL 로그

- `show-sql`은 `System.out`으로 출력돼서 로그 레벨 조절이 안 된다 → 끄고 로거로 대신 출력한다.
- `application-local.yml`에서만 로그를 켰다.

```yaml
logging:
  level:
    org.hibernate.SQL: debug              # 실행 SQL
    org.hibernate.orm.jdbc.bind: trace    # ?에 바인딩된 실제 값
```

- `bind: trace`는 비밀번호 같은 파라미터 값까지 그대로 찍히므로 **로컬 전용**이다.

### ddl-auto (프로필별)

| 프로필 | 값 | 의미 |
|---|---|---|
| local | `update` | 엔티티 변경을 테이블에 반영 (컬럼 추가는 되지만 삭제/변경은 안 됨) |
| test | `create-drop` | 시작할 때 생성, 끝날 때 삭제 |
| 운영(예정) | `validate` | 엔티티와 테이블이 맞는지 검사만. 스키마 변경은 Flyway 같은 마이그레이션 도구로 |

## 5. Spring Security 기본 설정

```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .formLogin(AbstractHttpConfigurer::disable)
    .httpBasic(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
        .anyRequest().permitAll()   // TODO: JWT 필터 적용 후 authenticated()로 전환
    );
```

| 설정 | 이유 |
|---|---|
| `csrf` 끄기 | CSRF는 쿠키 기반 세션 인증을 노리는 공격. JWT를 헤더로 보내는 REST API에서는 필요 없음 |
| `formLogin`, `httpBasic` 끄기 | 로그인 화면이 아니라 API로 로그인할 예정 |
| `STATELESS` | 서버에 세션을 만들지 않음. 요청마다 토큰으로 인증 |
| `/error` 허용 | 안 열어두면 예외 발생 시 에러 응답 대신 401/403이 나가서 원인을 알기 어려움 |
| `anyRequest().permitAll()` | 아직 JWT 인증이 없어서 임시로 전체 허용 |

- Spring Security 의존성만 추가하면 **모든 요청이 막히고** 기본 로그인 화면이 뜬다. 그래서 설정을 바로 만들어야 한다.
- `PasswordEncoder`(BCrypt)를 빈으로 등록해 뒀다. 회원 비밀번호는 평문이 아니라 해시로 저장한다. (`Member` 생성자의 파라미터 이름이 `encodedPassword`인 이유)

## 6. Swagger (springdoc)

- `http://localhost:8080/swagger-ui.html`에서 API 문서를 보고 바로 호출해 볼 수 있다.
- `SwaggerConfig`에 JWT Bearer 인증 스키마를 등록해 뒀다 → Swagger UI의 Authorize 버튼에 토큰을 넣으면 모든 요청 헤더에 `Authorization: Bearer ...`가 붙는다.

## 7. Testcontainers

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4");
    }
}
```

- 테스트를 실행하면 **테스트용 MySQL 컨테이너를 자동으로 띄우고**, 끝나면 정리한다.
- `@ServiceConnection`이 컨테이너의 url/username/password를 datasource에 자동으로 연결한다 → `application-test.yml`에 접속 정보가 없는 이유.
- H2 같은 인메모리 DB 대신 운영과 **같은 MySQL 8.4**로 테스트한다. 문법 차이나 예약어 문제를 테스트 단계에서 잡을 수 있다.
- Docker만 실행 중이면 되고 `docker compose up`은 필요 없다.

```java
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ShopApplicationTests {
    @Test
    void contextLoads() { }
}
```

- 스프링 컨텍스트 전체가 정상적으로 뜨는지 확인하는 테스트. 엔티티 매핑이 잘못됐거나 설정이 틀리면 여기서 실패한다.

## 8. 패키지 구조

```
com.lsch.shop
├── domain                  # 도메인(기능)별로 묶음
│   ├── member/entity
│   ├── category/entity
│   ├── product/entity
│   └── order/entity
└── global                  # 모든 도메인이 같이 쓰는 코드
    ├── config              # Security, Swagger, JPA Auditing 설정
    ├── entity              # BaseTimeEntity
    └── error               # ErrorCode, 예외, 전역 예외 처리
```

- 계층별(controller/service/repository를 최상위로)이 아니라 **도메인별**로 나눴다. 기능 하나를 수정할 때 관련 파일이 한 폴더에 모여 있다.
