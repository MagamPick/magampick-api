# products

매장의 일반 상품(정상가 메뉴) 테이블. 사장이 등록·조회한다. 마감 임박 상품(`ClearanceItem`) 과는 별도 엔티티.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 상품 식별자 |
| store_id | BIGINT | N | FK → stores.id | 소속 매장 |
| name | VARCHAR(50) | N |  | 상품명 |
| regular_price | NUMERIC(12, 0) | N | CHECK > 0 | 정상가 (원, 정수) |
| image_url | VARCHAR(500) | Y |  | 대표 사진 URL (선택, 최대 1장) |
| status | VARCHAR(10) | N | CHECK | `ON_SALE`, `SOLD_OUT`. 등록 시 ON_SALE |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

> `deleted_at` 은 본 이슈(#56) 에서 도입하지 않음. 후속 "상품 삭제" 이슈에서 마이그레이션으로 추가.

## 인덱스

- `products_pkey` (`id`)
- `idx_products_store_id` (`store_id`)
- `uq_products_store_name` UNIQUE (`store_id`, `name`)

## 제약

- `fk_products_store` FK `store_id → stores.id`
- `chk_products_status` CHECK `status IN ('ON_SALE', 'SOLD_OUT')`
- `chk_products_regular_price_positive` CHECK `regular_price > 0`
- `uq_products_store_name` UNIQUE `(store_id, name)` — 매장 내 상품명 유니크

## 관계

- `products.store_id` → `stores.id` (N:1)
- 마감 임박 상품 전환 시 `clearance_items.product_id` → `products.id` (N:1, nullable — 후속 이슈)

## 정책 결정 (#56)

- **이미지 선택 (최대 1장)**: features.md / product.md 에 다중 이미지 요구 없음. `image_url` 컬럼으로 충분, NULL 허용 → `product_images` 별도 테이블 미도입. 셀러가 이미지 없이도 등록 가능.
- **매장 카테고리만, 상품 카테고리 없음**: features 에 "상품 카테고리" 요구 없음. 매장 카테고리(`store_categories`) 와는 별개. 필요 시 후속 이슈.
- **해시태그 없음**: 해시태그는 리뷰 작성 시 선택 태그(`#맛있어요` 등) 용도. 상품 도메인에 붙지 않음.
- **매장 status = APPROVED 필수**: 등록 시 매장 승인 상태가 아니면 `STORE_NOT_APPROVED` (403). 조회는 status 무관 — 본인 매장이면 허용.
- **이미지 인프라**: stores 와 동일 `StorageService` 추상화 재사용 (로컬 = `LocalStorageService`, prod = S3 교체). 5MB / jpg·png·webp.
- **권한**: 셀러 본인 매장만 등록·조회. 본인 매장 아니면 `STORE_ACCESS_DENIED` (403).
- **품절(SOLD_OUT) / 수정 / 삭제**: 본 이슈 scope 밖. 후속 이슈에서 처리.
