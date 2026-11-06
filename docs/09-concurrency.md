# 09. 재고 동시성 (Lost Update, 데드락, 비관적 락)

## 목표

- 같은 상품에 주문이 동시에 몰릴 때 재고가 정확히 줄어드는지 확인한다.
- 문제를 **테스트로 재현**하고, 락 방식을 바꿔 가며 **같은 테스트로 비교**한다.

---

## 1. 재현 테스트: `OrderConcurrencyTest`

- 재고 100인 상품에, 수량 1 주문 100건을 스레드 32개로 동시에 보낸다.
- `CountDownLatch` 두 개를 쓴다.
  - `start(1)`: 모든 작업이 같은 출발선에서 기다렸다가 동시에 시작한다.
  - `done(100)`: 메인 스레드가 100건이 끝날 때까지 기다린다.
- 작업 스레드에서 난 예외는 테스트를 실패시키지 않고 사라진다. 그래서 `try-catch`로 직접 잡아서 성공/실패 건수와 예외 종류를 집계한다.
- 검증: `남은 재고 == 100 - 성공 건수`. 성공한 주문만큼 정확히 줄었는지 본다.
- 테스트 클래스에는 `@Transactional`을 붙이지 않는다. 주문마다 각자의 트랜잭션으로 커밋되어야 실제 경쟁이 일어난다.

```java
for (int i = 0; i < THREAD_COUNT; i++) {
    executor.submit(() -> {
        try {
            start.await();
            orderService.createOrder(memberId, request);
            success.incrementAndGet();
        } catch (Exception e) {
            fail.incrementAndGet();
            errors.merge(e.getClass().getSimpleName(), 1, Integer::sum);
        } finally {
            done.countDown();
        }
    });
}
start.countDown();
done.await(30, TimeUnit.SECONDS);
```

---

## 2. 결과 비교

| | 락 없음 | 낙관적 락 (`@Version`) | 비관적 락 (`PESSIMISTIC_WRITE`) |
|---|---|---|---|
| 성공 | 14~17 | 10~11 | **100** |
| 실패 | 83~86 | 89~90 | 0 |
| 실패 원인 | 데드락 (`CannotAcquireLockException`) | 데드락 78 + 버전 충돌 (`ObjectOptimisticLockingFailureException`) 11~12 | 없음 |
| 남은 재고 | **92** (성공 17건이면 83이어야 함) | 89~90 (성공 건수와 일치) | 0 |
| 테스트 | ❌ | ✅ | ✅ |

- 수치는 여러 번 실행한 범위다. Gradle은 입력이 같으면 테스트 결과를 캐시하므로, 반복 실행할 때는 `./gradlew test --tests "*OrderConcurrencyTest" --rerun`을 쓴다.

---

## 3. 락 없음: 문제가 두 개였다

### 3-1. Lost Update

- 주문은 17건 저장됐는데 재고는 8만 줄었다.
- 여러 트랜잭션이 같은 재고 100을 읽는다. 각자 1을 빼서 dirty checking으로 `UPDATE product SET stock = 99`를 보낸다. 나중에 커밋한 값이 앞의 값을 **덮어쓴다**.
- 재고 차감을 엔티티 값으로 계산하는 방식(`stock = stock - quantity`를 자바에서 계산)이라 생기는 문제다.

### 3-2. 데드락

- 예상하지 못한 결과였다. 실패 83건이 전부 MySQL `Deadlock found when trying to get lock`이었다.
- 원인(추정):
  1. `order_item`을 INSERT하면 FK(`product_id`) 검사 때문에 InnoDB가 부모 행인 `product`에 **S 락(공유 락)**을 건다.
  2. 커밋할 때 dirty checking으로 `UPDATE product`가 나가는데, 이건 **X 락(배타 락)**이 필요하다.
  3. 트랜잭션 A와 B가 모두 S 락을 쥔 채 X 락을 기다린다. 서로 상대의 S 락이 풀리기를 기다리므로 데드락이 생기고, MySQL이 한쪽을 롤백한다.
- Hibernate는 flush할 때 INSERT를 UPDATE보다 먼저 보낸다. 그래서 "S 락을 먼저 잡고 나중에 X 락으로 올리는" 순서가 항상 만들어진다.
- 확인하지 못한 것: `SHOW ENGINE INNODB STATUS`로 데드락 로그를 직접 보지는 않았다.

---

## 4. 낙관적 락 (`@Version`): 정합성은 맞지만 대부분 실패

```java
@Version
private Long version;
```

- UPDATE가 `UPDATE product SET stock = ?, version = ? WHERE id = ? AND version = ?`로 나간다. 그 사이 다른 트랜잭션이 커밋해서 버전이 바뀌었으면 수정된 행이 0개가 되고, `ObjectOptimisticLockingFailureException`이 난다.
- 그래서 **Lost Update는 사라졌다.** 재고 = 100 − 성공 건수가 맞는다.
- 하지만 **데드락은 그대로 남았다.** 낙관적 락은 DB 락을 잡지 않고 UPDATE 시점에 버전만 비교한다. 그래서 FK의 S 락 → X 락 구조가 바뀌지 않는다.
- 재고가 충분한데 주문 90%가 실패한다. 쓰려면 재시도가 필요한데, 경쟁이 심하면 재시도끼리 다시 충돌한다.
- 이 실험은 비교용으로만 하고 코드는 되돌렸다.

---

## 5. 비관적 락: 적용한 방법

```java
// ProductRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.id in :ids order by p.id")
List<Product> findAllByIdForUpdate(@Param("ids") List<Long> ids);
```

```java
// OrderService.createOrder
List<Product> products = productRepository.findAllByIdForUpdate(productIds);
```

- `PESSIMISTIC_WRITE`는 MySQL에서 `SELECT ... FOR UPDATE`로 나간다. 상품을 **조회하는 순간 X 락**을 잡는다.
- 다른 트랜잭션은 같은 상품을 `FOR UPDATE`로 읽으려다 기다린다. 앞 트랜잭션이 커밋한 뒤에 최신 재고를 읽으므로 100 → 99 → 98 … 순서대로 줄어든다. **Lost Update가 해결됐다.**
- 처음부터 X 락을 쥐고 있으므로 FK 검사의 S 락은 자기 자신의 락과 충돌하지 않는다. 다른 트랜잭션은 시작 단계에서 줄을 선다. **데드락도 함께 사라졌다.**
- 락은 트랜잭션이 끝날 때(커밋/롤백) 풀린다. 그래서 `@Transactional` 안에서 호출해야 의미가 있다. `OrderService`는 클래스 레벨에 이미 붙어 있다.

### `order by p.id`를 붙인 이유

- 주문 A가 상품 [1, 2], 주문 B가 [2, 1] 순서로 락을 잡으면, A는 1을 쥐고 2를 기다리고 B는 2를 쥐고 1을 기다린다. 데드락이다.
- 항상 ID 오름차순으로 락을 잡으면 순서가 같아져서 이 경우가 생기지 않는다.
- 이번 테스트는 상품 1개라 이 경우는 검증하지 않았다.

---

## 6. 방식 비교와 선택 이유

| | 비관적 락 | 낙관적 락 | 조건부 UPDATE |
|---|---|---|---|
| 방법 | `SELECT ... FOR UPDATE` | `@Version` + 버전 비교 | `UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?` |
| 충돌 시 | 기다림 | 예외 → 재시도 필요 | 영향받은 행 0 → 재고 부족 처리 |
| 장점 | 정확함, 도메인 로직(`decreaseStock`) 유지 | DB 락 없음, 충돌이 드물면 가벼움 | 락 대기가 가장 짧음 |
| 단점 | 대기로 처리량 감소, 커넥션 점유 | 충돌이 잦으면 실패/재시도 폭증 | 엔티티 로직을 우회, 영속성 컨텍스트와 값이 어긋남 |
| 어울리는 데이터 | 같은 행에 쓰기가 몰림 (재고) | 충돌이 드묾 (회원 정보 수정) | 단순한 숫자 증감 |

- **비관적 락을 선택했다.** 재고는 인기 상품에 쓰기가 몰리는 데이터라 낙관적 락으로는 실패가 너무 많았다. 또 `OrderItem.create()` → `Product.decreaseStock()`으로 재고 규칙을 엔티티에 둔 설계(08장)를 그대로 유지할 수 있다.
- 조건부 UPDATE는 실험하지 않았다. 트래픽이 더 커지면 검토한다.

---

## 7. 남은 것 / 검토할 것

- 락 대기 시간 제한: 지금은 MySQL 기본값(`innodb_lock_wait_timeout` 50초)을 따른다. `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))` 같은 힌트로 줄이는 방법이 있다.
- 서버가 여러 대여도 DB 락이라 그대로 동작한다. 다만 DB 부하가 문제가 되면 Redis 분산 락이나 재고 차감 큐를 고려한다.
- SQL 로그에서 `for update`를 직접 확인하지 못했다. 테스트 리포트 XML에서 찾지 못했고, 결과(100건 성공, 데드락 0)로 동작을 확인했다.
- 주문 취소 API를 만들 때 재고를 복구하는 `increaseStock`에도 같은 락을 쓴다.

## 면접에서 한 줄로

> "동시 주문 100건 테스트로 Lost Update를 재현했는데, FK 때문에 생기는 데드락도 함께 발견했습니다. 낙관적 락은 Lost Update는 막았지만 데드락이 남아 90%가 실패했고, 조회 시점에 X 락을 잡는 비관적 락으로 두 문제를 모두 해결했습니다. 여러 상품을 주문할 때의 데드락을 막으려고 ID 순서로 락을 잡았습니다."
