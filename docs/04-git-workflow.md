# 04. Git 작업 흐름

## 1. 커밋 메시지 규칙

```
<타입>: <요약>

- 변경 내용 1
- 변경 내용 2
```

| 타입 | 용도 | 예 |
|---|---|---|
| `feat` | 기능 추가 | `feat: 미션2 Order, OrderItem 엔티티 및 주문 금액 계산` |
| `fix` | 버그 수정 | `fix: 주문 총액에 수량이 반영되지 않는 문제` |
| `chore` | 설정, 빌드 등 기능과 무관한 작업 | `chore: 미션0 프로젝트 초기 세팅` |
| `docs` | 문서 | `docs: 학습 기록 정리` |
| `refactor` | 동작 변경 없는 코드 개선 | |
| `test` | 테스트만 추가/수정 | |

## 2. 브랜치 → 머지 흐름

```powershell
# 1. 작업 브랜치 만들기
git switch -c feat/mission2-entity

# 2. 작업하고 커밋
git add <파일들>
git commit -m "feat: ..."

# 3. main에 합치기
git switch main
git merge feat/mission2-entity

# 4. 다 쓴 브랜치 삭제
git branch -d feat/mission2-entity
```

- `main`에 바로 커밋하지 않고 브랜치에서 작업하면, 작업 도중에도 `main`은 항상 정상 상태로 유지된다.
- 브랜치 이름은 `feat/작업내용` 형태로.

### Fast-forward 머지

```
Updating 916e8ea..9ff154f
Fast-forward
```

- 브랜치를 만든 뒤로 `main`에 새 커밋이 없으면, 머지 커밋을 만들지 않고 `main`을 앞으로 **이동**만 시킨다.
- 히스토리가 한 줄로 깔끔하게 남는다.

### `-d` vs `-D`

| 옵션 | 동작 |
|---|---|
| `git branch -d` | 이미 머지된 브랜치만 삭제. 머지 안 됐으면 거부 → **안전** |
| `git branch -D` | 머지 여부 상관없이 강제 삭제 → 커밋을 잃을 수 있음 |

브랜치를 지워도 커밋은 머지된 `main`에 그대로 남는다.

## 3. GitHub 원격 저장소 연결

```powershell
git remote add origin https://github.com/cheul-95/shop-api.git
git push -u origin main
```

- `origin`: 원격 저장소의 별칭 (관례적인 이름)
- `-u`: 로컬 `main`이 `origin/main`을 추적하도록 설정. 다음부터는 `git push`만 입력하면 된다.
- `git remote -v`: 연결된 원격 저장소 확인

### 빈 저장소로 만들기

GitHub에서 저장소를 만들 때 **"Add a README file"을 체크하지 않는다.**
체크하면 원격에 README 커밋이 생겨서, 로컬 히스토리와 달라 바로 푸시할 수 없다.

### 원격에 이미 커밋이 있을 때

| 방법 | 명령 | 결과 |
|---|---|---|
| 원격 덮어쓰기 | `git push --force-with-lease` | 원격 커밋을 버리고 로컬 히스토리로 교체 |
| 합치기 | `git pull --allow-unrelated-histories` 후 충돌 해결 | 양쪽 보존, 머지 커밋 생김 |

이번에는 원격의 README가 2줄짜리 자동 생성 파일이라 덮어쓰기를 선택했다.

### `--force` 대신 `--force-with-lease`

- `--force`: 원격 상태와 상관없이 무조건 덮어씀.
- `--force-with-lease`: 원격이 **내가 마지막으로 확인한 상태일 때만** 덮어씀. 그 사이 누가 푸시했다면 거부한다 → 남의 작업을 실수로 지우지 않는다.

## 4. 푸시 전에 확인할 것

- [ ] `application*.yml`, `docker-compose.yml`에 **실제 서버** 비밀번호나 API 키가 없는지
- [ ] `.env`가 `.gitignore`에 있는지
- [ ] 로컬 Docker 개발용 기본값(`shop`/`root`)은 올라가도 괜찮다. 외부에서 접속할 수 없고, 다른 사람이 clone 받아 바로 실행하는 데 필요하다.
- [ ] 한 번 푸시한 비밀번호는 커밋을 지워도 히스토리에 남는다 → 노출됐다면 비밀번호 자체를 바꿔야 한다.
