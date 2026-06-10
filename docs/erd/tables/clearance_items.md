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
| pickup_start_at | TIMESTAMP | N | | 픽업 시작 시각 (KST). **사용자 입력 아님 — 등록 시각으로 서버 자동 설정** (이슈 #5) |
| pickup_end_at | TIMESTAMP | N | | 픽업 종료 시각 (KST). 등록 당일만 허용 |
| status | VARCHAR(20) | N | CHECK | `OPEN`, `SOLD_OUT`, `CLOSED` |
| close_reason | VARCHAR(30) | Y | CHECK | `EXPIRED`, `SOLD_OUT`, `MANUAL`. OPEN 상태이면 NULL |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |

## 인덱스

- `clearance_items_pkey` (`id`)
- `idx_clearance_items_store_id` (`store_id`)
- `idx_clearance_items_product_id` (`product_id`)
- `uq_clearance_items_product_open` UNIQUE (`product_id`) WHERE `status = 'OPEN'` — 한 상품당 OPEN 1개 제한
- `idx_clearance_items_status_pickup_end_at` (`status`, `pickup_end_at`) — 자동 마감 스케줄러 쿼리용
- `idx_clearance_items_name_trgm` GIN (`name` gin_trgm_ops) — Phase 9 검색: ILIKE 부분 일치 및 word_similarity 자동완성 가속

## 제약

- `fk_clearance_items_store` FK `store_id → stores.id`
- `fk_clearance_items_product` FK `product_id → products.id`
- `chk_clearance_items_status` CHECK `status IN ('OPEN', 'SOLD_OUT', 'CLOSED')`
- `chk_clearance_items_close_reason` CHECK `close_reason IN ('EXPIRED', 'SOLD_OUT', 'MANUAL')`
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
- **픽업창**: `pickup_end_at.toLocalDate() == 등록 당일 (KST)` + `pickup_end_at > now(KST)` 검증. `pickup_start_at` 은 서버 자동 설정이므로 요청 필드에 없음.
- **soft-delete 미도입**: `deleted_at` 없음. CLOSED 상태가 종료를 나타냄. 날짜가 바뀌면 새 row 등록.
- **권한**: 등록 시 매장 APPROVED 필수. 셀러 조회는 매장 상태 무관 (본인이면 허용).

### #69 — 수정·마감

- **수정 가능 필드**: `sale_price` / `total_quantity` / `pickup_end_at`. `pickup_start_at` 은 서버 자동 설정, 수정 불가. `name` / `regular_price` (스냅샷) / `status` 는 PATCH 노출 X.
- **수정 시 `remaining_quantity` 동기화**: `total_quantity` 변경 시 `remaining_quantity = total_quantity`. 주문 미구현 상태에서만 유효 — 계층 5 연결 시 재검토.
- **수정·마감 가능 상태**: `OPEN` 만. `CLOSED` / `SOLD_OUT` → `CLEARANCE_ITEM_NOT_OPEN` (409).
- **수동 마감 멱등**: `POST /close` 재호출 시 이미 `CLOSED` 면 no-op + 200. `uq_clearance_items_product_open` 해제로 동일 상품 재등록 가능.
- **자동 마감**: `@Scheduled(cron = "0 */5 * * * *")` — 5분 주기 bulk UPDATE. `status = OPEN AND pickup_end_at < now(KST)` → `CLOSED` + `close_reason = EXPIRED` 전환. 단일 인스턴스 가정 (졸업 프로젝트).
- **마감 사유**: `close_reason` — 마감 트리거 자동 기록. `EXPIRED`(시간 만료) / `SOLD_OUT`(수량 소진) / `MANUAL`(사장 강제 마감). OPEN 상태이면 NULL.
- **상품 삭제 차단**: `status = OPEN` 인 떨이가 있는 상품은 soft delete 불가. 먼저 떨이를 마감해야 삭제 가능.
