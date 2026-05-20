# clearance_items

일반 상품(`products`)을 당일 할인가로 판매하는 마감 임박 상품 테이블. 사장이 일반 상품을 전환·등록하며, 당일 픽업 기간 내에 소비자가 주문할 수 있다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 마감 임박 상품 식별자 |
| store_id | BIGINT | N | FK → stores.id | 소속 매장 |
| product_id | BIGINT | Y | FK → products.id | 원본 일반 상품 (NULL = 미래 독립 등록 예정, 현재는 앱 레벨 필수) |
| name | VARCHAR(50) | N | | 상품명 스냅샷 (등록 시점 product.name 복사) |
| regular_price | NUMERIC(12, 0) | N | CHECK > 0 | 정상가 스냅샷 (원, 정수) |
| sale_price | NUMERIC(12, 0) | N | CHECK > 0 | 판매가 (원, 정수). 항상 regular_price 미만 |
| total_quantity | INT | N | CHECK > 0 | 등록 수량 |
| remaining_quantity | INT | N | CHECK >= 0 | 잔여 수량 (주문 시 감소) |
| pickup_start_at | TIMESTAMP | N | | 픽업 시작 시각 (KST) |
| pickup_end_at | TIMESTAMP | N | | 픽업 종료 시각 (KST). 등록 당일만 허용 |
| status | VARCHAR(20) | N | CHECK | `OPEN`, `SOLD_OUT`, `CLOSED` |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |

## 인덱스

- `clearance_items_pkey` (`id`)
- `idx_clearance_items_store_id` (`store_id`)
- `idx_clearance_items_product_id` (`product_id`)
- `uq_clearance_items_product_open` UNIQUE (`product_id`) WHERE `status = 'OPEN'` — 한 상품당 OPEN 1개 제한

## 제약

- `fk_clearance_items_store` FK `store_id → stores.id`
- `fk_clearance_items_product` FK `product_id → products.id`
- `chk_clearance_items_status` CHECK `status IN ('OPEN', 'SOLD_OUT', 'CLOSED')`
- `chk_clearance_items_sale_price_positive` CHECK `sale_price > 0`
- `chk_clearance_items_regular_price_positive` CHECK `regular_price > 0`
- `chk_clearance_items_total_quantity_positive` CHECK `total_quantity > 0`
- `chk_clearance_items_remaining_quantity_nonneg` CHECK `remaining_quantity >= 0`

## 관계

- `clearance_items.store_id` → `stores.id` (N:1)
- `clearance_items.product_id` → `products.id` (N:1, nullable)
- `order_items.clearance_item_id` → `clearance_items.id` (N:1, 후속 이슈)

## 정책 결정

### #67 — 등록·셀러 조회

- **전환 전용**: product_id 는 앱 레벨에서 필수. DB nullable 은 미래 독립 등록 지원 여지. `products.status = ON_SALE + deleted_at IS NULL` 인 상품만 전환 가능.
- **OPEN 1개 제한**: `uq_clearance_items_product_open` partial unique index — 동일 product_id 로 OPEN 상태가 이미 존재하면 409.
- **가격**: `sale_price` 단독 입력. `discount_rate` 는 응답 전용 계산 필드 (`1 - sale_price / regular_price`), DB 저장 X.
- **스냅샷**: `name` + `regular_price` 는 등록 시점 product 값 복사. 이후 product 수정 영향 없음.
- **이미지**: `image_url` 컬럼 없음. 응답 시 `product.image_url` 라이브 참조.
- **픽업창**: `pickup_end_at.toLocalDate() == 등록 당일 (KST)` + `pickup_start_at < pickup_end_at` 검증.
- **soft-delete 미도입**: `deleted_at` 없음. CLOSED 상태가 종료를 나타냄. 날짜가 바뀌면 새 row 등록.
- **권한**: 등록 시 매장 APPROVED 필수. 셀러 조회는 매장 상태 무관 (본인이면 허용).
