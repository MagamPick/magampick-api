# review_replies

사장이 리뷰에 달아주는 답글. 리뷰 1개당 최대 1개 (UNIQUE).
Phase 4: read-only (리뷰 목록 응답에 ownerReply 포함). write(답글 작성/수정)는 Phase 7 구현 예정.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 답글 식별자 |
| review_id | BIGINT | N | FK → reviews.id, UNIQUE | 대상 리뷰 (1:1) |
| seller_id | BIGINT | N | FK → sellers.id | 답글 작성 사장 |
| content | VARCHAR(500) | N | | 답글 내용 |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 (Phase 7 수정 기능 활성화 시 사용) |

## 인덱스

- `review_replies_pkey` (`id`)
- `uq_review_replies_review` UNIQUE (`review_id`) — 리뷰당 답글 1개 제한

## 제약

- `fk_review_replies_review` FK `review_id → reviews.id`
- `fk_review_replies_seller` FK `seller_id → sellers.id`
- `uq_review_replies_review` UNIQUE `review_id`

## 관계

- `review_replies.review_id` → `reviews.id` (1:1, UNIQUE)
- `review_replies.seller_id` → `sellers.id` (N:1)

## 설계 메모

- **soft-delete 미도입**: 답글은 hard-delete (Phase 7 삭제 기능 구현 시 결정 재검토 가능)
- **매핑**: `Review.reviewReply` → `@OneToOne(mappedBy = "review")`, `ReviewReply.review` → `@OneToOne @JoinColumn(unique = true)`
- **Phase 4 read 지원**: `StoreReviewResponse.ownerReply` (nullable) — `review.getReviewReply().getContent()`
- **write/lifecycle Phase 7**: 답글 작성(매장 소유자 권한 검증) / 수정은 Phase 7
