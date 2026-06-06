# review_images

리뷰에 첨부된 사진 목록. sort_order 오름차순으로 표시 순서 지정. 최대 3장 제한은 앱 레이어에서 검증 (Phase 7 write 구현 시).

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 리뷰 이미지 식별자 |
| review_id | BIGINT | N | FK → reviews.id | 소속 리뷰 |
| url | VARCHAR(500) | N | | 이미지 URL (OCI Object Storage) |
| sort_order | INT | N | | 표시 순서 (0-based 오름차순) |

## 인덱스

- `review_images_pkey` (`id`)
- `idx_review_images_review_id` (`review_id`) — 리뷰별 이미지 일괄 조회

## 제약

- `fk_review_images_review` FK `review_id → reviews.id`

## 관계

- `review_images.review_id` → `reviews.id` (N:1)

## 설계 메모

- **soft-delete 미도입**: 리뷰 삭제 시 CASCADE 하드 딜리트 (`CascadeType.ALL, orphanRemoval = true`)
- **BaseEntity 미사용**: `created_at` / `updated_at` 없음 — 생성 후 변경 없는 불변 데이터
- **사진 최대 3장 제한**: DB 제약 아닌 앱 레이어 검증 (Phase 7 리뷰 작성 구현 시 추가)
- **URL 저장**: 업로드는 OCI Object Storage → URL 저장. `StorageService` 통해 presigned URL 생성
- **Phase 4 read 지원**: `StoreReviewResponse.photos[]` 로 응답. `Review.reviewImages` @OrderBy sort_order ASC
- **write/lifecycle Phase 7**: 이미지 업로드/삭제는 Phase 7
