# favorites

소비자-매장 즐겨찾기 관계. M:N 조인 테이블 (surrogate PK).

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `customer_id` | `BIGINT` | NOT NULL, FK → `customers.id` | 즐겨찾기한 소비자 |
| `store_id` | `BIGINT` | NOT NULL, FK → `stores.id` | 즐겨찾기된 매장 |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | 즐겨찾기 등록 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `uk_favorites_customer_store` | `(customer_id, store_id)` | 중복 등록 차단 + 목록 조회 index |

`(customer_id, store_id)` UNIQUE 인덱스가 선두 컬럼 `customer_id` prefix로 `WHERE customer_id = ?` 쿼리를 커버하므로 별도 인덱스 불필요.

## 관계

- `customers` 1:N `favorites` — 한 소비자가 여러 매장 즐겨찾기
- `stores` 1:N `favorites` — 한 매장이 여러 소비자에게 즐겨찾기됨
- 합쳐서 `customers` M:N `stores`

## 비즈니스 규칙

- `APPROVED` 상태 매장만 등록 가능 (`PENDING`/`REJECTED` 시도 → `STORE_NOT_APPROVED`)
- 등록·해제 모두 멱등 처리 (중복 등록 무시, 미등록 해제도 204 반환)
- 등록 개수 상한 **50개** (초과 시 `FAVORITE_LIMIT_REACHED` 409). 동시 추가 레이스는 `uk_favorites_customer_store` 위반을 멱등 성공으로 수렴
