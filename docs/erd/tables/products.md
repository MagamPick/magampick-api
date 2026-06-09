# products

매장의 일반 상품(정상가 메뉴) 테이블. 사장이 등록·수정·삭제·품절 처리한다. 마감 임박 상품(`ClearanceItem`) 과는 별도 엔티티.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 상품 식별자 |
| store_id | BIGINT | N | FK → stores.id | 소속 매장 |
| name | VARCHAR(50) | N |  | 상품명 |
| regular_price | NUMERIC(12, 0) | N | CHECK > 0 | 정상가 (원, 정수) |
| image_url | VARCHAR(500) | Y |  | 대표 사진 URL (선택, 최대 1장) |
| status | VARCHAR(10) | N | CHECK | `ON_SALE`, `SOLD_OUT`. 등록 시 ON_SALE |
| category | VARCHAR(20) | N | CHECK, DEFAULT 'ETC' | `BAKERY`, `BEVERAGE`, `DESSERT`, `ETC`. 등록 시 ETC |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |
| deleted_at | TIMESTAMP | Y |  | 소프트 삭제 시각. NULL = 삭제되지 않음 |

## 인덱스

- `products_pkey` (`id`)
- `idx_products_store_id` (`store_id`)
- `uq_products_store_name_active` UNIQUE (`store_id`, `name`) WHERE `deleted_at IS NULL` — 삭제된 상품과 동명 재등록 허용
- `idx_products_name_trgm` GIN (`name` gin_trgm_ops) — Phase 9 검색: ILIKE 부분 일치 및 word_similarity 자동완성 가속

## 제약

- `fk_products_store` FK `store_id → stores.id`
- `chk_products_status` CHECK `status IN ('ON_SALE', 'SOLD_OUT')`
- `chk_products_category` CHECK `category IN ('BAKERY','BEVERAGE','DESSERT','ETC')`
- `chk_products_regular_price_positive` CHECK `regular_price > 0`

## 관계

- `products.store_id` → `stores.id` (N:1)
- 마감 임박 상품 전환 시 `clearance_items.product_id` → `products.id` (N:1, nullable — 후속 이슈)

## 정책 결정

### #56 — 등록·조회
- **이미지 선택 (최대 1장)**: features.md / product.md 에 다중 이미지 요구 없음. `image_url` 컬럼으로 충분, NULL 허용 → `product_images` 별도 테이블 미도입. 셀러가 이미지 없이도 등록 가능.
- **매장 카테고리만, 상품 카테고리 없음**: features 에 "상품 카테고리" 요구 없음. 매장 카테고리(`store_categories`) 와는 별개. 필요 시 후속 이슈.
- **해시태그 없음**: 해시태그는 리뷰 작성 시 선택 태그(`#맛있어요` 등) 용도. 상품 도메인에 붙지 않음.
- **매장 status = APPROVED 필수**: 등록 시 매장 승인 상태가 아니면 `STORE_NOT_APPROVED` (403). 조회는 status 무관 — 본인 매장이면 허용.
- **이미지 인프라**: stores 와 동일 `StorageService` 추상화 재사용 (로컬 = `LocalStorageService`, prod = S3 교체). 5MB / jpg·png·webp.
- **권한**: 셀러 본인 매장만 등록·조회. 본인 매장 아니면 `STORE_ACCESS_DENIED` (403).

### #65 — 수정·삭제·품절
- **Soft Delete (`deleted_at`)**: 통계 / 마감 임박 상품 이력 추적 대비 (계층 8). `deleted_at IS NULL` 필터를 Repository 메서드에 명시 — `@SQLRestriction` 미사용 (관리자 기능 확장 시 JPA 우회 불필요).
- **Partial Unique Index**: 소프트 삭제로 인한 `UNIQUE (store_id, name)` 충돌 방지. `WHERE deleted_at IS NULL` partial index 로 교체 → 삭제된 상품과 동명 재등록 허용.
- **수정 가능 필드**: `name` / `regular_price` / `image_url` 만. `status` 는 별도 액션 엔드포인트로 관리.
- **이미지 수정**: 새 파일 업로드 시 URL 교체. 명시적 제거(null로 변경) 미지원 — 기존 이미지 파일 storage 물리 삭제는 출시 시점 S3 lifecycle policy 별도 이슈.
- **품절 멱등**: `sold-out` / `restock` 이미 그 상태면 no-op (200 OK). 잘못된 전이 거부 X.
- **권한 (수정·삭제·품절)**: 셀러 본인 매장만. 매장 status 무관 (등록만 APPROVED 필수).
