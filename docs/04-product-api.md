# 04. 상품 조회 API

> 커밋: `b4a086c feat: 미션3 상품 조회 API (검색/페이징, N+1 해결, 에러 코드 enum 이름 통일)`

## 미션 요구사항

- `GET /api/products?keyword=&page=&size=`: 이름 검색 + 페이징
- `GET /api/products/{id}`: 상세 조회, 없으면 404 `PRODUCT_NOT_FOUND`
- 엔티티가 아니라 DTO로 응답
- `data.sql`로 샘플 데이터 넣기
- **완료 조건**: 목록 조회 시 SELECT 2번(목록 + 개수)만, 카테고리 N+1 없음

---

## 1. 계층 구조

```
ProductController  →  ProductService  →  ProductRepository
  (요청/응답)          (트랜잭션, 변환)       (조회)
```

```java
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public Page<ProductResponse> getProducts(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) {
            return productRepository.findAll(pageable)
                    .map(ProductResponse::from);
        }
        return productRepository.findByNameContaining(keyword, pageable)
                .map(ProductResponse::from);
    }

    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }
}
```

- 없는 상품은 `BusinessException(PRODUCT_NOT_FOUND)`을 던지기만 한다. 404 응답은 미션 1의 `GlobalExceptionHandler`가 만든다. 컨트롤러에는 `try-catch`가 없다.
- `Pageable`은 컨트롤러 파라미터로 받기만 하면 Spring이 `?page=0&size=10&sort=price,desc`를 자동으로 바인딩한다.

### `@Transactional(readOnly = true)`

- 조회만 하는 서비스라 클래스 전체에 붙였다. 나중에 쓰기 메서드가 생기면 그 메서드에만 `@Transactional`을 따로 붙인다.
- Hibernate가 변경 감지(dirty checking)용 스냅샷을 만들지 않아서 조금 가볍다.
- DTO 변환(`ProductResponse::from`)도 트랜잭션 안에서 해야 한다. 밖에서 LAZY 연관관계를 꺼내면 `LazyInitializationException`이 날 수 있다(OSIV 설정에 따라 다름).

## 2. DTO 응답: `ProductResponse`

```java
public record ProductResponse(Long id, String name, String categoryName, int price, int stock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getStock()
        );
    }
}
```

### 엔티티를 그대로 응답하지 않는 이유

- **API 스펙이 엔티티에 묶인다**: 엔티티 필드를 바꾸면 API 응답도 바뀐다.
- **LAZY 연관관계 문제**: `category`가 프록시 상태로 JSON 직렬화되면 예외가 나거나 의도하지 않은 쿼리가 나간다.
- **양방향 연관관계**: `Order ↔ OrderItem` 같은 관계는 직렬화할 때 무한 순환이 생긴다.
- **노출하면 안 되는 필드**: `Member.password` 같은 필드가 그대로 나갈 수 있다.

`category` 전체가 아니라 화면에 필요한 `categoryName`만 펼쳐서 내보냈다.

## 3. N+1 문제와 `@EntityGraph`

### 문제

`Product.category`는 `LAZY`다. 목록을 조회하고 DTO로 바꾸면서 `getCategory().getName()`을 호출하는 순간 카테고리 SELECT가 추가로 나간다.

```
SELECT product ...                    ← 목록 1번
SELECT category WHERE id = 1          ← 상품을 DTO로 바꾸면서
SELECT category WHERE id = 2          ← 카테고리 종류만큼 추가
...
SELECT count(...) FROM product        ← 페이징 개수
```

같은 카테고리는 영속성 컨텍스트에 이미 있어서 다시 조회하지 않는다. 그래도 카테고리 종류가 많을수록 쿼리가 늘어난다.

**DTO 코드가 잘못된 게 아니다.** `getCategory().getName()`은 어딘가에서 꼭 해야 한다. 문제는 그 시점에 category가 로딩되지 않았다는 것이고, 고칠 곳은 Repository다.

### 해결: `@EntityGraph`

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "category")
    Page<Product> findByNameContaining(String name, Pageable pageable);
}
```

- 메서드 이름 쿼리는 그대로 두고, 조회할 때 `category`를 **같이 가져오라고** 지정한다. `product join category`로 한 번에 조회된다.
  - `@JoinColumn(nullable = false)`라서 Hibernate가 inner join을 썼다. nullable이면 left join이 된다.
- `Page`용 count 쿼리는 Spring Data가 join 없이 따로 만든다.

### 키워드가 없을 때: `findAll`도 다시 선언

```java
@Override
@EntityGraph(attributePaths = "category")
Page<Product> findAll(Pageable pageable);
```

```java
if (!StringUtils.hasText(keyword)) {
    return productRepository.findAll(pageable).map(ProductResponse::from);
}
return productRepository.findByNameContaining(keyword, pageable).map(ProductResponse::from);
```

- `keyword`가 `null`, `""`, 공백이면 전체 조회. `StringUtils.hasText()`로 세 경우를 한 번에 거른다.
- `JpaRepository`가 주는 `findAll(Pageable)`을 그대로 쓰면 `@EntityGraph`가 없어서 **N+1이 다시 생긴다.** `@Override`로 다시 선언해서 어노테이션만 덧씌웠다.
- 처음에는 `findByNameContaining(Pageable)`처럼 파라미터 없는 메서드를 만들었다. 이름이 `NameContaining`이라 파라미터가 하나 필요해서 **컴파일은 되지만 앱 시작 시 쿼리 생성에 실패**한다. 메서드 이름 쿼리는 실행해 봐야 검증된다.

### `attributePaths`는 필드 이름, `@JoinColumn`은 컬럼 이름

| 위치 | 기준 | 값 |
|---|---|---|
| `@EntityGraph(attributePaths = ...)` | 엔티티의 **필드 이름** | `"category"` |
| `@JoinColumn(name = ...)` | DB의 **FK 컬럼 이름** | `"category_id"` |

헷갈려서 `@JoinColumn`까지 바꿨다가 되돌렸다. [트러블슈팅 3](troubleshooting.md) 참고.

### 다른 방법: `@Query` + `join fetch`

```java
@Query(
        value = "select p from Product p join fetch p.category c " +
                "where p.name like concat('%', :keyword, '%')",
        countQuery = "select count(p) from Product p " +
                "where p.name like concat('%', :keyword, '%')"
)
Page<Product> searchByName(@Param("keyword") String keyword, Pageable pageable);
```

- JPQL을 직접 쓴다. `Product`, `p.category`는 테이블/컬럼이 아니라 엔티티/필드 기준이다.
- `Containing`처럼 `%`를 자동으로 붙여주지 않아서 `concat`으로 직접 붙인다.
- count 쿼리에는 채울 엔티티가 없어서 `join fetch`를 쓸 수 없다. 그래서 `countQuery`를 따로 지정한다.

### 실무에서는

| 상황 | 주로 쓰는 것 |
|---|---|
| 기본 안전장치 | `hibernate.default_batch_fetch_size` (LAZY 로딩을 `IN (...)` 한 번으로 묶음, 1+N → 1+1) |
| 단순 조회 + 연관 1~2개 | `@EntityGraph` |
| 고정된 조건이 있는 조회 | `@Query` + `join fetch` |
| 동적 검색 / 복잡한 조회 | QueryDSL (+ DTO 직접 조회) |

- `default_batch_fetch_size`로는 쿼리가 1+1이 되므로 이번 완료 조건(SELECT 2번)은 못 맞춘다.
- **컬렉션(`@OneToMany`)을 fetch join하면서 페이징하면 안 된다.** DB에서 페이징하지 못하고 전체를 메모리에 올린 뒤 자른다(`HHH90003004` 경고). 이때는 batch fetch size로 해결한다.

## 4. 기본 정렬: `@PageableDefault`

```java
@PageableDefault(size = 10, sort = "id", direction = DESC) Pageable pageable
```

- 클라이언트가 `page`, `size`, `sort`를 안 보내면 이 값을 쓴다. `?sort=price,asc`처럼 보내면 그 값이 우선이다.
- 속성 없이 `@PageableDefault`만 붙이면 `size = 10`, 정렬 없음이다. 어노테이션이 아예 없을 때 Spring 기본 크기는 20이다.
- `sort`에는 컬럼명(`product_id`)이 아니라 **필드 이름**(`id`)을 쓴다. SQL에서는 `order by p1_0.product_id desc`가 된다.

### 최신순인데 `createdAt`이 아니라 `id`로 정렬한 이유

- `data.sql`이 모든 행을 `NOW()`로 넣어서 `created_at`이 전부 같다.
- 정렬 값이 겹치면 그 행들끼리는 순서가 보장되지 않는다. 페이지를 넘길 때 같은 상품이 두 번 나오거나 빠질 수 있다.
- `id`는 auto increment라 최신순과 같고 값이 겹치지 않는다.
- 겹칠 수 있는 값(가격 등)으로 정렬할 때는 마지막에 유일한 값(`id`)을 두 번째 기준으로 넣는다. 방향이 다르면 `@SortDefault`를 쓴다.

## 5. 샘플 데이터: `data.sql`

```sql
INSERT IGNORE INTO category (category_id, name, created_at, updated_at) VALUES
(1, '전자제품', NOW(), NOW()), ...;

INSERT IGNORE INTO product (product_id, category_id, name, price, stock, created_at, updated_at) VALUES
(1, 1, '노트북', 1200000, 10, NOW(), NOW()), ...;
```

- FK 때문에 **category를 먼저** 넣어야 한다.
- `ddl-auto: update`라 재시작해도 데이터가 남는다. PK를 직접 지정하고 `INSERT IGNORE`를 써서 다시 실행해도 중복으로 들어가지 않게 했다.
- SQL로 직접 넣으면 JPA Auditing이 동작하지 않는다. `created_at`, `updated_at`은 `NOW()`로 채운다.

### `application-local.yml` 설정

| 설정 | 이유 |
|---|---|
| `spring.jpa.defer-datasource-initialization: true` | 기본은 `data.sql`이 JPA 테이블 생성보다 먼저 실행돼서, 테이블이 없어 실패한다 |
| `spring.sql.init.mode: always` | 기본값 `embedded`는 H2 같은 내장 DB에서만 실행한다. MySQL에서는 직접 켜야 한다 |
| `spring.sql.init.encoding: UTF-8` | 지정하지 않으면 OS 기본 인코딩(Windows는 MS949)으로 읽어서 한글이 깨질 수 있다 |

local 프로필에만 넣었다. 운영 DB에서 앱이 뜰 때마다 SQL이 실행되면 안 된다.

## 6. 완료 조건 확인

- [x] `GET /api/products` → 목록(`join category` + `order by product_id desc`) 1번 + count 1번. 상품별 category SELECT 없음
- [x] `GET /api/products?keyword=노트북` → 목록(`join category` + `like ?`) 1번. count는 생략됨 (아래 참고)
- [x] `?sort=price,asc` → `order by price`로 기본 정렬을 덮어씀
- [x] `GET /api/products/999` → 404 `{"code":"PRODUCT_NOT_FOUND", "message":"상품을 찾을 수 없습니다."}` (미션 1 완료 조건)

### count 쿼리가 생략되는 경우

Spring Data는 전체 개수를 이미 알 수 있으면 count 쿼리를 보내지 않는다.

- 첫 페이지인데 결과가 페이지 크기보다 적을 때: 노트북 검색 4개 < size 10 → 전체 4개
- 마지막 페이지일 때: `offset + 이번 페이지 결과 수` = 전체 개수

그래서 "SELECT 2번"은 정확히는 **최대 2번**이다.
