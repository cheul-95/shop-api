# 작업 규칙

이 프로젝트에서 브랜치, 커밋, PR을 다루는 규칙입니다.
혼자 하는 프로젝트지만 **팀에서 일하는 것처럼** main에 직접 커밋하지 않고, 모든 변경은 PR로 남깁니다.

## 1. 작업 흐름

```bash
# 1. 최신 main에서 브랜치 만들기
git switch main
git pull
git switch -c feat/mission7-order-cancel

# 2. 작업하고 커밋 (테스트 통과 확인 후)
./gradlew test
git add <파일들>
git commit

# 3. 푸시하고 PR 만들기
git push -u origin feat/mission7-order-cancel
gh pr create --base main          # 또는 GitHub 화면에서

# 4. GitHub에서 머지한 뒤 로컬 정리
git switch main
git pull
git branch -d feat/mission7-order-cancel
```

- `main`은 항상 테스트가 통과하는 상태로 유지합니다.
- 머지는 GitHub의 **Merge pull request**(머지 커밋)로 합니다. PR 번호가 히스토리에 남아서 어떤 PR로 들어온 변경인지 추적할 수 있습니다.
- `git branch -d`는 머지된 브랜치만 지웁니다. 머지되지 않았으면 거부하니 `-D`(강제 삭제)보다 안전합니다.

## 2. 브랜치 이름

```
<타입>/<작업 내용>
```

| 예 | 용도 |
|---|---|
| `feat/mission6-jwt-filter` | 미션 기능 |
| `feat/bonus-order` | 보너스 미션 |
| `fix/order-total-price` | 버그 수정 |
| `docs/readme` | 문서만 수정 |

- 타입은 아래 커밋 타입과 같은 것을 씁니다.
- 영어 소문자와 `-`만 씁니다.

## 3. 커밋 메시지

```
<타입>: <요약>

- 변경 내용 1
- 변경 내용 2
```

| 타입 | 용도 | 예 |
|---|---|---|
| `feat` | 기능 추가 | `feat: 미션4 회원가입/로그인 API (BCrypt, 중복 이메일 409, 로그인 실패 401)` |
| `fix` | 버그 수정 | `fix: 주문 총액에 수량이 반영되지 않는 문제` |
| `refactor` | 동작은 그대로 두고 코드 개선 | `refactor: 주문 상품 조회를 findAllById로 변경` |
| `test` | 테스트만 추가/수정 | `test: 주문 동시성 테스트 추가` |
| `docs` | 문서만 수정 | `docs: 미션6 학습 기록 추가 (07-jwt-filter, 트러블슈팅 5/6, 로드맵, API 목록)` |
| `chore` | 설정, 빌드, 의존성 등 기능과 무관한 작업 | `chore: MySQL 포트를 localhost에만 바인딩` |

- 요약은 한국어로, **무엇을 했는지** 한 줄로 씁니다. 마침표를 찍지 않습니다.
- 미션 작업이면 요약에 미션 번호를 넣습니다. (`미션5`, `보너스 미션`)
- 본문에는 바꾼 내용을 `-` 목록으로 씁니다. 왜 바꿨는지가 코드만으로 드러나지 않으면 이유도 적습니다.
- **코드와 문서는 커밋을 나눕니다.** 미션 하나는 보통 `feat` 커밋 1개 + `docs` 커밋 1개로 끝납니다.
- 커밋 하나에는 하나의 목적만 담습니다. 리뷰할 때나 되돌릴 때 단위가 분명해집니다.

## 4. Pull Request

- 템플릿: [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md). PR을 만들면 자동으로 채워집니다.
- 제목은 대표 커밋의 요약과 같은 형식으로 씁니다. 예: `feat: 보너스 미션 주문 생성 API (POST /api/orders)`
- 본문에 들어갈 것
  - **개요**: 무엇을 했는지 한두 줄
  - **변경 사항**: 클래스나 파일 단위로
  - **테스트**: `./gradlew test` 결과, curl이나 Swagger로 직접 확인한 요청과 응답
  - **체크리스트**: 미션 완료 조건, ERD/API 목록 갱신, 민감 정보 없음
  - **리뷰 포인트**: 고민한 부분, 다른 방법과 비교한 내용, 확신이 없는 부분

## 5. 머지 전에 확인할 것

- [ ] `./gradlew test` 전체 통과 (Docker Desktop이 실행 중이어야 함)
- [ ] API가 추가/변경됐으면 [README](README.md)의 API 목록, [에러 코드](docs/error-codes.md) 갱신
- [ ] 엔티티가 바뀌었으면 README의 ERD 갱신
- [ ] 미션 작업이면 [학습 기록](docs/README.md)의 문서와 로드맵 갱신
- [ ] `.env`, JWT secret, 비밀번호가 커밋에 들어가지 않았는지 확인
