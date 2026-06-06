# order_items

주문에 포함된 떨이(clearance_item) 상품 라인 아이템. 한 주문에 여러 떨이를 담을 수 있다.
Phase 4 리뷰 집계에서 `JOIN order_items` 로 떨이 평점 계산에 사용됨.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 주문 항목 식별자 |
| order_id | BIGINT | N | FK → orders.id | 소속 주문 |
| clearance_item_id | BIGINT | N | FK → clearance_items.id | 주문한 떨이 상품 |
| quantity | INT | N | CHECK > 0 | 주문 수량 |
| unit_price | NUMERIC(12, 0) | N | CHECK > 0 | 주문 시점 단가 스냅샷 (원, 정수) |
| subtotal | NUMERIC(12, 0) | N | CHECK > 0 | quantity × unit_price (원, 정수) |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |

## 인덱스

- `order_items_pkey` (`id`)
- `idx_order_items_order_id` (`order_id`)
- `idx_order_items_clearance_item_id` (`clearance_item_id`)

## 제약

- `fk_order_items_order` FK `order_id → orders.id`
- `fk_order_items_clearance_item` FK `clearance_item_id → clearance_items.id`
- `chk_order_items_quantity_positive` CHECK `quantity > 0`
- `chk_order_items_unit_price_positive` CHECK `unit_price > 0`
- `chk_order_items_subtotal_positive` CHECK `subtotal > 0`

## 관계

- `order_items.order_id` → `orders.id` (N:1)
- `order_items.clearance_item_id` → `clearance_items.id` (N:1)

## 설계 메모

- **soft-delete 미도입**: 주문 항목은 종속 데이터 — 주문 soft-delete 로 충분
- **단가 스냅샷**: `unit_price` = 주문 시점 `clearance_item.sale_price` 복사. 이후 떨이 가격 변경 영향 없음
- **메뉴 상품(products) 직접 주문 없음**: 현재 주문은 떨이(clearance_item)만 담김 → 상품 평점 = 떨이 평점만 존재
- **Phase 4 read 지원**: `ReviewRepository.findClearanceItemRatingStats` 가 이 테이블 JOIN 해 떨이 평점 집계
- **write/lifecycle Phase 5**: 주문 생성 시 잔여 수량 감소(optimistic lock) 등은 Phase 5 에서 구현
