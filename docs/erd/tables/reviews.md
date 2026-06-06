# reviews

픽업 완료 주문에 소비자가 남기는 리뷰. 주문 1개당 리뷰 1개 (UNIQUE).
Phase 4: read-only (목록 조회 + 평점 집계). write(작성/수정/삭제)는 Phase 7 구현 예정.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 리뷰 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 리뷰 작성자 |
| order_id | BIGINT | N | FK → orders.id, UNIQUE | 리뷰의 기반 주문 (1:1) |
| store_id | BIGINT | N | FK → stores.id | 리뷰 대상 매장 (목록/집계 필터용) |
| rating | INT | N | CHECK 1..5 | 별점 (1~5) |
| content | VARCHAR(300) | Y | | 리뷰 텍스트 (생략 가능) |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 (Phase 7 수정 기능 활성화 시 사용) |
| deleted_at | TIMESTAMP | Y | | soft-delete 시각. NULL = 정상 |

## 인덱스

- `reviews_pkey` (`id`)
- `uq_reviews_order` UNIQUE (`order_id`) — 주문당 리뷰 1개 제한
- `idx_reviews_store_id` (`store_id`) — 목록 조회 / 평점 집계 필터
- `idx_reviews_customer_id` (`customer_id`) — 내 리뷰 조회 (Phase 7)

## 제약

- `fk_reviews_customer` FK `customer_id → customers.id`
- `fk_reviews_order` FK `order_id → orders.id`
- `fk_reviews_store` FK `store_id → stores.id`
- `uq_reviews_order` UNIQUE `order_id`
- `chk_reviews_rating` CHECK `rating BETWEEN 1 AND 5`

## 관계

- `reviews.customer_id` → `customers.id` (N:1)
- `reviews.order_id` → `orders.id` (1:1, UNIQUE)
- `reviews.store_id` → `stores.id` (N:1)
- `review_images.review_id` → `reviews.id` (1:N, 최대 3장)
- `review_replies.review_id` → `reviews.id` (1:1, UNIQUE)
- `review_tags.review_id` → `reviews.id` (1:N, @ElementCollection)

## 설계 메모

- **store_id 중복 저장**: `order.store_id` 와 동일하지만 리뷰 집계 쿼리 단순화를 위해 reviews 테이블에 별도 저장
- **soft-delete**: `deleted_at` 적용. 집계 쿼리는 `deleted_at IS NULL` 필터 필수
- **매장 평점** = `AVG(rating) WHERE store_id = ? AND deleted_at IS NULL`
- **상품(떨이) 평점** = `AVG(r.rating) JOIN order_items oi WHERE oi.clearance_item_id = ? AND deleted_at IS NULL`
- **Phase 4 read 지원**: `GET /api/v1/stores/{storeId}/reviews`, `GET .../summary`
- **write/lifecycle Phase 7**: 리뷰 작성(픽업 완료 검증)/수정(7일)/삭제/사장 답글은 Phase 7
