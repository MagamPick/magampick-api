# orders

소비자가 떨이(clearance_item) 상품을 픽업 예약한 주문 테이블.
Phase 4 리뷰 read 를 위한 스키마 선반영 — 상태 전이 로직 / 결제·포인트 컬럼은 Phase 5/6 에서 추가 예정.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 주문 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 주문 소비자 |
| store_id | BIGINT | N | FK → stores.id | 주문 대상 매장 |
| status | VARCHAR(20) | N | CHECK | `RECEIVED` / `PREPARING` / `READY` / `PICKED_UP` / `CANCELED` |
| total_price | NUMERIC(12, 0) | N | CHECK >= 0 | 주문 총액 (원, 정수) |
| pickup_time | TIMESTAMP | Y | | 픽업 희망 시각 (KST). 주문 생성 시 지정, Phase 5 구현 |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |
| deleted_at | TIMESTAMP | Y | | soft-delete 시각. NULL = 정상 |

## 인덱스

- `orders_pkey` (`id`)
- `idx_orders_customer_id` (`customer_id`)
- `idx_orders_store_id` (`store_id`)

## 제약

- `fk_orders_customer` FK `customer_id → customers.id`
- `fk_orders_store` FK `store_id → stores.id`
- `chk_orders_status` CHECK `status IN ('RECEIVED', 'PREPARING', 'READY', 'PICKED_UP', 'CANCELED')`
- `chk_orders_total_price_nonneg` CHECK `total_price >= 0`

## 관계

- `orders.customer_id` → `customers.id` (N:1)
- `orders.store_id` → `stores.id` (N:1)
- `order_items.order_id` → `orders.id` (1:N)
- `reviews.order_id` → `orders.id` (1:1, UNIQUE)

## 설계 메모

- **결제·포인트 컬럼 미적용**: `discount_amount` / `point_used` / `final_price` 는 Phase 6 ALTER 로 추가 예정
- **soft-delete**: `deleted_at` 적용 (취소·삭제된 주문 히스토리 보존)
- **상태 전이 로직 미적용**: Phase 5 주문 lifecycle 구현 시 Service 레이어에서 전이 검증 추가
- **Phase 4 read 지원**: 리뷰 집계 쿼리(`JOIN r.order o JOIN o.orderItems oi`)가 이 테이블 참조
