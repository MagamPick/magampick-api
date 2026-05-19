# stores

매장 테이블. 사장이 등록 신청 후 관리자 승인을 거쳐 노출된다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 매장 식별자 |
| seller_id | BIGINT | N | FK → sellers.id | 소유 사장 |
| name | VARCHAR(50) | N |  | 매장명 |
| road_address | VARCHAR(200) | N |  | 도로명 주소 |
| jibun_address | VARCHAR(200) | Y |  | 지번 주소 |
| detail_address | VARCHAR(100) | Y |  | 상세 주소 |
| zonecode | VARCHAR(10) | N |  | 우편번호 5자리 |
| location | GEOGRAPHY(POINT,4326) | N |  | PostGIS 위경도. GIST 인덱스 |
| phone | VARCHAR(20) | N |  | 매장 전화번호 |
| description | VARCHAR(500) | Y |  | 매장 소개 |
| image_url | VARCHAR(500) | N |  | 대표 사진 URL |
| status | VARCHAR(10) | N | CHECK | `PENDING`, `APPROVED`, `REJECTED` |
| rejection_reason | VARCHAR(500) | Y |  | 반려 사유. REJECTED 시 설정 |
| deleted_at | TIMESTAMP | Y |  | 소프트 삭제 시각 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `stores_pkey` (`id`)
- `idx_stores_seller_id` (`seller_id`)
- `idx_stores_status` (`status`)
- `idx_stores_location` GIST (`location`)

## 제약

- `fk_stores_seller` FK `seller_id → sellers.id`
- `chk_stores_status` CHECK `status IN ('PENDING', 'APPROVED', 'REJECTED')`

## 관계

- `stores.seller_id` → `sellers.id` (N:1)
- `store_store_categories.store_id` → `stores.id` (M:N via 조인 테이블)
- 카테고리 1~3개 필수 (`@Size(min=1, max=3)` — 앱 레벨 제약)

## 정책 결정 (#48)

- **반려 후 재신청**: 미지원. 재신청 필요 시 관리자 문의. 히스토리 필요해지면 `store_review_histories` 테이블로 마이그레이션
- **자동 승인 토글**: `magampick.stores.auto-approve` (local/dev default=true, prod=false)
- **이미지**: 대표 1장. 5MB / jpg·png·webp. prod는 S3 등 인터페이스 교체
