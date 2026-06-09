# inquiries

1:1 문의 테이블. 소비자·사장이 제출하고 관리자가 답변한다.
`author_role` + `author_id` 로 소비자/사장을 polymorphic 참조 (FK 없음 — 역할에 따라 다른 테이블 참조).

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 문의 식별자 |
| author_role | VARCHAR(20) | N | CHECK ('CUSTOMER','SELLER') | 작성자 역할 |
| author_id | BIGINT | N | | 작성자 ID (customers.id 또는 sellers.id) |
| category | VARCHAR(20) | N | CHECK (9개 값) | 문의 카테고리 |
| title | VARCHAR(40) | N | | 문의 제목 (2~40자) |
| content | TEXT | N | | 문의 내용 (10~1000자) |
| status | VARCHAR(20) | N | CHECK ('PENDING','ANSWERED'), DEFAULT 'PENDING' | 문의 상태 |
| answer_content | TEXT | Y | | 답변 본문 (답변 전 NULL) |
| answered_at | TIMESTAMP | Y | | 답변 시각 (답변 전 NULL) |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## category 값

| 값 | JSON 직렬화 | 설명 |
|---|---|---|
| PAYMENT | "payment" | 결제 |
| ORDER | "order" | 주문 |
| COUPON | "coupon" | 쿠폰 |
| ACCOUNT | "account" | 계정 |
| REPORT | "report" | 신고 |
| SETTLEMENT | "settlement" | 정산 |
| STORE | "store" | 매장 |
| PRODUCT | "product" | 상품 |
| ETC | "etc" | 기타 |

## status 값

| 값 | JSON 직렬화 | 설명 |
|---|---|---|
| PENDING | "pending" | 답변 대기 |
| ANSWERED | "answered" | 답변 완료 |

## 인덱스

| 인덱스명 | 컬럼 | 용도 |
|---|---|---|
| `inquiries_pkey` | `id` | PK |
| `idx_inquiries_author` | `(author_role, author_id, created_at DESC)` | 본인 문의 목록 조회 |
| `idx_inquiries_status` | `(status)` | 관리자 상태 필터 |

## 제약

- `chk_inquiries_author_role` CHECK `author_role IN ('CUSTOMER', 'SELLER')`
- `chk_inquiries_category` CHECK `category IN ('PAYMENT','ORDER','COUPON','ACCOUNT','REPORT','SETTLEMENT','STORE','PRODUCT','ETC')`
- `chk_inquiries_status` CHECK `status IN ('PENDING', 'ANSWERED')`

## 관계

- `author_role + author_id` → `customers.id` 또는 `sellers.id` (polymorphic, FK 미설정)
- 관리자 답변 후 `notifications` 에 INQUIRY 카테고리 알림 저장

## 정렬 규칙

- **본인 문의 목록**: `created_at DESC` (최신순)
- **관리자 문의 목록**: `status DESC → created_at DESC` (PENDING 우선 — 'P' > 'A' 알파벳 내림차순)

## 설계 메모

- **FK 미설정**: `author_role` 에 따라 참조 테이블이 달라지는 polymorphic 참조. application 레이어에서 스코프 검증.
- **category 부분집합 강제 X**: BE는 9개 값 모두 허용. FE가 역할별로 노출 카테고리 제한 (예: 사장에게 SETTLEMENT 노출, 소비자에게 REPORT 노출).
- **재답변/스레드/첨부**: 현 단계 Out of Scope (백로그).
- **Seed**: 없음 (유저 생성 데이터).
