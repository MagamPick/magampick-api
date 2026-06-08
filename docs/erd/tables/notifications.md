# notifications

수신함 알림 레코드. 발송된 알림을 수신자별로 보관 — 읽음 처리 / 조회에 사용한다.

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `receiver_type` | `VARCHAR(10)` | NOT NULL, CHECK IN (CUSTOMER, SELLER) | 수신자 종류 (polymorphic) |
| `receiver_id` | `BIGINT` | NOT NULL | 수신자 ID — `receiver_type` 에 따라 `customers.id` 또는 `sellers.id` |
| `category` | `VARCHAR(20)` | NOT NULL, CHECK | 알림 카테고리 (DEAL, ORDER, REVIEW, BENEFIT, SYSTEM, REFUND, SETTLEMENT, NOTICE) |
| `title` | `VARCHAR(200)` | NOT NULL | 알림 제목 |
| `body` | `TEXT` | NOT NULL | 알림 본문 |
| `link` | `VARCHAR(500)` | NULL | 클릭 시 이동 링크 (선택) |
| `is_read` | `BOOLEAN` | NOT NULL, DEFAULT FALSE | 읽음 여부 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `chk_receiver_type` | `receiver_type` | CUSTOMER/SELLER 만 허용 |
| `chk_category` | `category` | 유효한 카테고리 값만 허용 |
| `idx_notifications_receiver` | `(receiver_type, receiver_id, created_at DESC)` | 수신자별 목록 조회 최적화 |
| `idx_notifications_receiver_category` | `(receiver_type, receiver_id, category, created_at DESC)` | 카테고리별 목록 조회 최적화 |

## 관계

- **FK 없음 (의도)** — `(receiver_type, receiver_id)` polymorphic 참조. 소비자/사장이 공유 `users` 테이블 없이 별도 테이블이라 단일 FK 불가. 무결성은 `receiver_type` CHECK + 애플리케이션 레이어가 담당.

## 비즈니스 규칙

- 읽음 처리: 단건(`PATCH /{id}/read`) 또는 전체(`PATCH /read-all`).
- 카테고리별 필터: 소비자는 `deal`/`order` 세그먼트 조회 지원.
- 사장은 카테고리 필터 없이 전체 조회.
- 삭제 기능 없음 — 보관 전용 (Phase 7 이후 소멸 정책 검토 예정).
