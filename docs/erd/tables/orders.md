# orders

소비자 주문 테이블. Phase 5A: 주문 생성 + stub 결제 확정.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 주문 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 주문 소비자 |
| store_id | BIGINT | N | FK → stores.id | 주문 대상 매장 |
| status | VARCHAR(20) | N | CHECK (7값) | 주문 상태 (아래 참조) |
| total_price | NUMERIC(12, 0) | N | CHECK >= 0 | 상품 결제 base (payTotal = normal − discount). **사장 매출·정산 기준** (쿠폰·포인트 차감 전) |
| pickup_time | TIMESTAMP | Y | | 픽업 시각 (KST). SLOT 유형에만 채워짐, ASAP = NULL |
| pickup_type | VARCHAR(10) | Y | CHECK (ASAP\|SLOT) | 픽업 유형. Phase 5A 이전 주문 = NULL |
| pickup_code | VARCHAR(4) | Y | | 4자리 픽업 인증 코드. Phase 5A 이전 주문 = NULL |
| memo | VARCHAR(80) | Y | | 픽업 요청 메모 (사장 전달용) |
| normal_total | NUMERIC(12, 0) | Y | | 정상가 합계 (항목별 regularPrice × qty 합) |
| discount_total | NUMERIC(12, 0) | Y | | 할인 합계 (떨이 항목의 할인분 합) |
| coupon_discount | NUMERIC(12, 0) | Y | | 쿠폰 할인액 (일반상품분). 미사용 = NULL (Phase8) |
| point_used | BIGINT | Y | | 사용 포인트 (1P=1원). 미사용 = NULL (Phase8) |
| earned_points | BIGINT | Y | | 적립 포인트 = floor(final_amount/100). 0 = NULL (픽업완료 시 실적립, Phase8) |
| final_amount | NUMERIC(12, 0) | Y | | **실결제 현금** = total_price − coupon_discount − point_used. 결제 검증 기준 (Phase8) |
| user_coupon_id | BIGINT | Y | FK → user_coupons.id | 사용 쿠폰 인스턴스. 미사용 = NULL (Phase8) |
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
- `orders.user_coupon_id` → `user_coupons.id` (N:1, nullable, Phase8)
- `order_items.order_id` → `orders.id` (1:N)
- `payments.order_id` → `orders.id` (1:1, UNIQUE)
- `reviews.order_id` → `orders.id` (1:1, UNIQUE)

## 설계 메모

- **total_price = 상품 base (정산 기준)**: 쿠폰·포인트 차감 전 상품 결제액. 정산은 플랫폼 보전 정책 — 사장은 total_price 기준 정산, 쿠폰·포인트 비용은 플랫폼 부담 (정산 쿼리 SUM(total_price) 유지)
- **final_amount = 실결제 현금**: 고객이 토스로 실제 결제하는 금액 (total_price − coupon_discount − point_used). 결제 승인 검증 기준. Phase8 ALTER 추가, 기존 행은 final_amount = total_price 백필
- **혜택 차감 시점**: 쿠폰 use·포인트 FIFO 차감은 **결제 성공 시**. 적립은 **픽업완료 시**. 취소·거절 시 복원 (환불 복원·회수·소멸 배치는 Phase8 후속)
- **pickup_type / pickup_code / normal_total / discount_total**: Phase 5A ALTER 로 추가. Phase 5A 이전 기존 행은 NULL
- **soft-delete**: `deleted_at` 적용 (취소·삭제된 주문 히스토리 보존)
- **orderNo**: DB 저장 X. Response 에서 id → `String.format("%04d", id)` 파생
