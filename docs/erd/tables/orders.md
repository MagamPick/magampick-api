# orders

소비자 주문 테이블. Phase 5A: 주문 생성 + stub 결제 확정.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 주문 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 주문 소비자 |
| store_id | BIGINT | N | FK → stores.id | 주문 대상 매장 |
| status | VARCHAR(20) | N | CHECK (7값) | 주문 상태 (아래 참조) |
| total_price | NUMERIC(12, 0) | N | CHECK >= 0 | 결제액 (payTotal). 쿠폰·포인트 Phase8 미포함 |
| pickup_time | TIMESTAMP | Y | | 픽업 시각 (KST). SLOT 유형에만 채워짐, ASAP = NULL |
| pickup_type | VARCHAR(10) | Y | CHECK (ASAP\|SLOT) | 픽업 유형. Phase 5A 이전 주문 = NULL |
| pickup_code | VARCHAR(4) | Y | | 4자리 픽업 인증 코드. Phase 5A 이전 주문 = NULL |
| memo | VARCHAR(80) | Y | | 픽업 요청 메모 (사장 전달용) |
| normal_total | NUMERIC(12, 0) | Y | | 정상가 합계 (항목별 regularPrice × qty 합) |
| discount_total | NUMERIC(12, 0) | Y | | 할인 합계 (떨이 항목의 할인분 합) |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |
| deleted_at | TIMESTAMP | Y | | soft-delete 시각. NULL = 정상 |

## status 값 (7값, Phase 5A~)

| 값 | 설명 |
|---|---|
| PENDING | 주문접수 — 결제 완료, 사장 수락 전 (5A 생성 기본값) |
| PREPARING | 준비중 |
| READY | 준비완료 |
| COMPLETED | 수령완료 |
| NO_SHOW | 미수령 |
| REJECTED | 사장 거절 |
| CANCELLED | 소비자 취소 |

## 인덱스

- `orders_pkey` (`id`)
- `idx_orders_customer_id` (`customer_id`)
- `idx_orders_store_id` (`store_id`)

## 제약

- `fk_orders_customer` FK `customer_id → customers.id`
- `fk_orders_store` FK `store_id → stores.id`
- `chk_orders_status` CHECK `status IN ('PENDING','PREPARING','READY','COMPLETED','NO_SHOW','REJECTED','CANCELLED')`
- `chk_orders_total_price_nonneg` CHECK `total_price >= 0`
- `chk_orders_pickup_type` CHECK `pickup_type IS NULL OR pickup_type IN ('ASAP','SLOT')`

## 관계

- `orders.customer_id` → `customers.id` (N:1)
- `orders.store_id` → `stores.id` (N:1)
- `order_items.order_id` → `orders.id` (1:N)
- `payments.order_id` → `orders.id` (1:1, UNIQUE)
- `reviews.order_id` → `orders.id` (1:1, UNIQUE)

## 설계 메모

- **total_price = payTotal**: 결제액. 쿠폰/포인트는 Phase8 에서 별도 컬럼 추가
- **pickup_type / pickup_code / normal_total / discount_total**: Phase 5A ALTER 로 추가. Phase 5A 이전 기존 행은 NULL
- **soft-delete**: `deleted_at` 적용 (취소·삭제된 주문 히스토리 보존)
- **orderNo**: DB 저장 X. Response 에서 id → `String.format("%04d", id)` 파생
