# store_categories

매장 카테고리 마스터 테이블. 초기 시드: 베이커리, 카페.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 카테고리 식별자 |
| name | VARCHAR(50) | N | UNIQUE | 카테고리 이름 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `store_categories_pkey` (`id`)
- `store_categories_name_key` UNIQUE (`name`)

## 관계

- `store_store_categories.store_category_id` → `store_categories.id`
