# order_items

주문에 포함된 상품 라인 아이템. Phase 5A: 떨이(DEAL) + 일반 상품(MENU) 혼합 지원. 스냅샷 필드로 조인 없이 표시 가능.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 주문 항목 식별자 |
| order_id | BIGINT | N | FK → orders.id | 소속 주문 |
| clearance_item_id | BIGINT | Y | FK → clearance_items.id | DEAL 항목. MENU = NULL |
| product_id | BIGINT | Y | FK → products.id | MENU 항목. DEAL = NULL |
| item_kind | VARCHAR(10) | Y | CHECK (DEAL\|MENU) | 항목 종류 |
| name | VARCHAR(255) | Y | | 상품명 스냅샷 |
| original_price | NUMERIC(12, 0) | Y | | 정상가 스냅샷 (DEAL = regularPrice, MENU = regularPrice) |
| image_url | VARCHAR(500) | Y | | 이미지 URL 스냅샷 |
| unit_price | NUMERIC(12, 0) | N | CHECK > 0 | 결제 단가 (DEAL = salePrice, MENU = regularPrice) |
| quantity | INT | N | CHECK > 0 | 주문 수량 (1~10) |
| subtotal | NUMERIC(12, 0) | N | CHECK > 0 | quantity × unit_price |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |

## 인덱스

- `order_items_pkey` (`id`)
- `idx_order_items_order_id` (`order_id`)
- `idx_order_items_clearance_item_id` (`clearance_item_id`)
- `idx_order_items_product_id` (`product_id`)

## 제약

- `fk_order_items_order` FK `order_id → orders.id`
- `fk_order_items_clearance_item` FK `clearance_item_id → clearance_items.id`
- `fk_order_items_product` FK `product_id → products.id`
- `chk_order_items_item_kind` CHECK `item_kind IS NULL OR item_kind IN ('DEAL','MENU')`
- `chk_order_items_item_ref` CHECK `(clearance_item_id IS NOT NULL AND product_id IS NULL) OR (clearance_item_id IS NULL AND product_id IS NOT NULL)`
- `chk_order_items_quantity_positive` CHECK `quantity > 0`
- `chk_order_items_unit_price_positive` CHECK `unit_price > 0`
- `chk_order_items_subtotal_positive` CHECK `subtotal > 0`

## 관계

- `order_items.order_id` → `orders.id` (N:1)
- `order_items.clearance_item_id` → `clearance_items.id` (N:1, nullable)
- `order_items.product_id` → `products.id` (N:1, nullable)

## 설계 메모

- **clearance_item_id / product_id 정확히 하나만 NOT NULL**: DB CHECK 로 강제 (`chk_order_items_item_ref`)
- **스냅샷 필드**: `name`, `original_price`, `image_url` = 주문 시점 스냅샷. 이후 상품 정보 변경 영향 없음
- **DEAL 재고 차감**: 주문 생성 시 `decrementStock` (조건부 UPDATE) 로 동시성 안전하게 차감
- **soft-delete 미도입**: 주문 항목은 종속 데이터 — 주문 soft-delete 로 충분
- **Phase 4 read 지원**: `ReviewRepository.findClearanceItemRatingStats` 가 이 테이블 JOIN 해 떨이 평점 집계
