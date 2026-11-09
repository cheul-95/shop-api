# 10. AWS 배포와 CI/CD (EC2, Docker Compose, GitHub Actions)

## 목표

- 로컬에서만 돌던 shop-api를 **AWS EC2**에 올려서 외부에서 Swagger로 호출할 수 있게 한다.
- main에 머지하면 **테스트 → 이미지 빌드 → 배포**가 자동으로 돌게 한다.
- 비밀 값(JWT secret, DB 비밀번호)이 이미지나 저장소에 들어가지 않게 한다.

---

## 1. 전체 구조

```
Developer ── merge to main
  │
  ▼
GitHub Actions
  ├─ test     ./gradlew test (Testcontainers MySQL)
  ├─ build    docker build → push ghcr.io/cheul-95/shop-api:{latest, sha}
  └─ deploy   SSH → docker compose pull && up -d → 헬스체크
                │
                ▼
AWS EC2 (Ubuntu 24.04, Elastic IP)
  └─ Docker Compose
       ├─ shop-api   Spring Boot, prod 프로필      :8080 공개
       ├─ shop-db    MySQL 8.4 + 볼륨              포트 비공개
       └─ .env       JWT_SECRET, DB 비밀번호 (서버에만 존재)
                ▲
Client ── http://<Elastic IP>:8080/swagger-ui.html
```

| 구성 | 선택 | 이유 |
|---|---|---|
| 서버 | EC2 1대 (Ubuntu 24.04) | 처음 배포라 구성 요소를 최소로 두고 동작부터 확인 |
| 실행 | Docker Compose (app + MySQL) | 로컬과 같은 방식으로 실행, 서버에는 Docker만 설치 |
| 이미지 저장소 | GHCR (GitHub Container Registry) | GitHub Actions의 `GITHUB_TOKEN`으로 바로 push/pull, 추가 계정과 비용 없음 |
| 배포 방식 | Actions에서 SSH로 접속해 `pull` + `up -d` | 서버에서 빌드하지 않아 작은 인스턴스의 메모리 부담이 없음 |
| IP | Elastic IP | 인스턴스를 껐다 켜도 주소가 바뀌지 않음 (Secrets와 포트폴리오 링크 유지) |

---

## 2. Dockerfile: multi-stage 빌드

```dockerfile
FROM eclipse-temurin:17-jdk AS build
...
RUN ./gradlew dependencies --no-daemon > /dev/null   # 의존성 레이어 캐시
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
RUN useradd --system --no-create-home spring
USER spring
COPY --from=build /workspace/build/libs/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=Asia/Seoul
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

- **multi-stage**: JDK와 Gradle 캐시는 빌드 stage에만 있고, 최종 이미지에는 JRE와 jar만 들어간다. 이미지가 작아지고 공격 표면도 줄어든다.
- **레이어 캐시**: gradle 파일을 먼저 복사해서 의존성을 받고, 그다음 `src`를 복사한다. 소스만 바뀌면 의존성 다운로드 레이어를 다시 쓰지 않는다.
- **`bootJar`만 실행**: `build`는 테스트까지 돌린다. Testcontainers는 컨테이너 안에서 Docker가 필요해서 이미지 빌드 중에는 돌릴 수 없다. 테스트는 CI의 `test` job에서 따로 돌린다.
- **non-root 사용자**: 컨테이너가 뚫려도 root 권한을 얻지 못하게 한다.
- **`MaxRAMPercentage=75.0`**: JVM 힙을 컨테이너 메모리 기준으로 잡는다. 메모리가 1~2GB인 인스턴스에서 OOM을 줄인다.
- **`.dockerignore`에 `.env`**: 빠뜨리면 `COPY`할 때 JWT secret이 이미지 레이어에 그대로 남는다.

---

## 3. prod 프로필 (`application-prod.yml`)

| 항목 | local | prod | 이유 |
|---|---|---|---|
| `.env` import | 있음 | 없음 | 운영은 컨테이너 환경변수로 주입한다 |
| DB 접속 정보 | 기본값 `shop/shop` | 기본값 없음 | 값을 빠뜨리면 약한 기본값으로 붙지 않고 바로 실패한다 (fail-fast) |
| `ddl-auto` | `update` | `${DDL_AUTO:validate}` | 엔티티 변경이 운영 스키마를 바꾸지 않게 한다. 안 맞으면 기동 실패 |
| `sql.init.mode` | `always` | `${SQL_INIT_MODE:never}` | 샘플 데이터(`data.sql`)가 재시작마다 실행되지 않게 한다 |
| 로그 | SQL debug, bind trace | info / warn | trace는 바인딩 값(비밀번호 등)을 남기고 양도 많다 |

- **첫 배포만** `.env`에 `DDL_AUTO=update`, `SQL_INIT_MODE=always`를 넣어 테이블과 샘플 데이터를 만들고, 그 뒤에는 지워서 기본값으로 돌린다.
- 이 수동 단계는 Flyway 같은 마이그레이션 도구로 바꿀 수 있다 (남은 것 참고).

---

## 4. `docker-compose.prod.yml`

- app은 `depends_on: condition: service_healthy`로 **MySQL 헬스체크가 통과한 뒤** 시작한다. 그냥 `depends_on`은 컨테이너 시작 순서만 보장하고, DB가 접속 가능한지는 보장하지 않는다.
- app은 `DB_HOST=mysql`로 접속한다. 같은 compose 네트워크 안에서는 **서비스 이름이 호스트 이름**이 된다.
- MySQL은 `ports`를 두지 않는다. 외부에서 3306으로 접속할 수 없고 app만 접근한다.
- 비밀 값은 `${JWT_SECRET:?...}` 형식이라 서버 `.env`에 없으면 compose가 실행 자체를 거부한다.
- app 이미지는 `ghcr.io/cheul-95/shop-api:${IMAGE_TAG:-latest}`. 배포할 때 커밋 sha를 태그로 넘겨서 **어떤 커밋이 떠 있는지** 알 수 있다.

---

## 5. GitHub Actions 파이프라인 (`.github/workflows/ci-cd.yml`)

```
PR → main           test
push(merge) → main  test → build → deploy
```

| job | 하는 일 |
|---|---|
| `test` | JDK 17 + Gradle 캐시, `./gradlew test` (Testcontainers MySQL). 실패하면 테스트 리포트를 artifact로 올린다 |
| `build` | Buildx로 이미지 빌드, GHCR에 `latest`와 커밋 sha 태그로 push. GitHub Actions 캐시로 레이어 재사용 |
| `deploy` | SSH로 EC2 접속 → `git checkout -B main origin/main` → GHCR 로그인 → `compose pull app` → `up -d` → 오래된 이미지 정리 → 외부에서 `/v3/api-docs` 헬스체크 |

- `needs`로 순서를 묶어서 **테스트가 실패하면 배포하지 않는다.**
- GHCR 인증은 job마다 발급되는 `GITHUB_TOKEN`을 쓴다. 서버에는 배포하는 동안만 로그인하고 끝나면 `docker logout` 한다.
- 등록한 Secrets: `EC2_HOST`(Elastic IP), `EC2_USER`(`ubuntu`), `EC2_SSH_KEY`(pem 내용).
- 배포 후 DB 컨테이너는 `Running` 그대로, app 컨테이너만 `Recreated` 된다. 데이터는 볼륨에 남는다.

---

## 6. 보안그룹

| 포트 | 소스 | 이유 |
|---|---|---|
| 22 (SSH) | 0.0.0.0/0 | GitHub Actions 러너 IP가 매번 바뀐다. Ubuntu AMI는 비밀번호 로그인이 꺼져 있고 키 인증만 허용한다 |
| 8080 | 0.0.0.0/0 | API, Swagger |
| 3306 | 열지 않음 | DB는 Docker 네트워크 안에서만 접근 |

- 22번을 전체로 연 것은 타협이다. 더 안전한 방법은 아래 "남은 것"에 정리했다.

---

## 7. 겪은 문제

자세한 내용은 [트러블슈팅](troubleshooting.md) 6~9번.

| 문제 | 원인 | 해결 |
|---|---|---|
| Docker 빌드 중 `./gradlew dependencies` 실패 | Gradle 배포판 다운로드 타임아웃 (wrapper 기본 10초, 재시도 0) | `networkTimeout=60000`, `retries=3` |
| 앱은 떴는데 브라우저에서 접속 안 됨 | 보안그룹에 8080 인바운드 규칙 없음 | 8080 규칙 추가 |
| CI에서 `./gradlew: Permission denied` | Windows에서 커밋해서 git에 실행 권한(100755)이 없음 | `git update-index --chmod=+x gradlew` |
| 컨테이너 로그 시각이 UTC (9시간 차이) | 컨테이너 JVM 기본 시간대가 UTC, JDBC `serverTimezone`과는 별개 | `ENV TZ=Asia/Seoul` |

---

## 8. 남은 것 / 검토할 것

- **HTTPS**: 도메인 + Nginx + Let's Encrypt, 또는 ALB + ACM. 지금은 `http://<IP>:8080`.
- **RDS 분리**: 지금은 DB가 앱과 같은 서버에 있어서 인스턴스가 죽으면 DB도 같이 죽는다. 백업과 장애 대응을 위해 RDS로 분리한다.
- **Flyway**: 첫 배포의 `DDL_AUTO=update` 수동 단계를 마이그레이션 스크립트로 대체한다.
- **SSH 대신 SSM**: GitHub OIDC로 AWS 역할을 받고 SSM Run Command로 배포하면 22번 포트를 닫을 수 있다.
- **무중단 배포**: 지금은 app 컨테이너를 재생성하는 동안 10~15초 정도 응답하지 않는다. 컨테이너 2개 + Nginx 전환(blue-green)으로 줄일 수 있다.
- **생성된 기본 비밀번호 로그**: `UserDetailsServiceAutoConfiguration`이 `Using generated security password`를 로그에 남긴다. JWT만 쓰므로 이 자동 설정을 끈다.

## 면접에서 한 줄로

> "EC2 한 대에 Docker Compose로 Spring Boot와 MySQL을 띄우고, GitHub Actions로 테스트 → GHCR 이미지 push → SSH 배포 → 헬스체크까지 자동화했습니다. 이미지는 multi-stage로 JRE만 담고 non-root로 실행하며, 비밀 값은 서버 `.env`와 GitHub Secrets로만 주입해서 이미지와 저장소에 남지 않게 했습니다. 서버에서 빌드하지 않고 CI에서 이미지를 만들어서 작은 인스턴스의 메모리 문제를 피했습니다."
