# review_tags

리뷰에 달린 고정 프리셋 태그. `@ElementCollection<ReviewTag>` 매핑 — surrogate PK 없이 (review_id, tag) 구성.
Phase 7 리뷰 작성 시 최종 확정/확장 예정. 현재 5종 초기값.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| review_id | BIGINT | N | FK → reviews.id | 소속 리뷰 |
| tag | VARCHAR(20) | N | CHECK | `FRESH` / `KIND` / `REVISIT` / `GENEROUS` / `GOOD_VALUE` |

## 인덱스

- `idx_review_tags_review_id` (`review_id`) — 리뷰별 태그 일괄 조회

## 제약

- `fk_review_tags_review` FK `review_id → reviews.id`
- `chk_review_tags_tag` CHECK `tag IN ('FRESH', 'KIND', 'REVISIT', 'GENEROUS', 'GOOD_VALUE')`

## 태그 프리셋 (ReviewTag enum)

| 값 | 한국어 라벨 |
|---|---|
| FRESH | 신선해요 |
| KIND | 친절해요 |
| REVISIT | 재방문 |
| GENEROUS | 양 많아요 |
| GOOD_VALUE | 가성비 좋아요 |

## 관계

- `review_tags.review_id` → `reviews.id` (N:1, @ElementCollection)

## 설계 메모

- **PK 없음**: Hibernate `@ElementCollection` 표준 — `(review_id, tag)` 로 구성하지만 DB 레벨 PK 제약 없음. 동일 리뷰에 같은 태그 중복은 `Set<ReviewTag>` 로 Java 레이어에서 방지
- **별도 엔티티 대신 @ElementCollection 선택 이유**: 태그는 독립 생명주기/연관 없음, 변경은 리뷰 전체 삭제 재생성 단위, Phase 4 read 에서는 리뷰와 함께 로드. Value Object 개념에 더 가까움
- **Phase 4 read 지원**: `Review.tags` lazy 로드 → `ReviewMapper.mapTagLabels()` → 한국어 라벨 목록으로 응답
- **write/lifecycle Phase 7**: 리뷰 작성 시 태그 선택 (다중 선택 가능, 0~5종). 확정 후 태그 종류 확장 가능
