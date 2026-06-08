# coupons

쿠폰 마스터/템플릿 테이블. SIGNUP(가입 축하) / EVENT(이벤트) 두 종류를 지원한다.
유효기간은 종류에 따라 고정일(EVENT·valid_until) 또는 발급일 기준 상대 일수(SIGNUP·validity_days) 로 결정된다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 쿠폰 마스터 식별자 |
| kind | VARCHAR(20) | N | CHECK ('SIGNUP','EVENT') | 쿠폰 종류 |
| label | VARCHAR(100) | N | | 쿠폰 이름 (UI 표시용) |
| discount_type | VARCHAR(20) | N | CHECK ('RATE','AMOUNT') | 할인 방식 |
| discount_value | INT | N | | 할인 값 (RATE=%, AMOUNT=원) |
| min_order | INT | N | DEFAULT 0 | 최소 주문 금액 (원) |
| valid_until | DATE | Y | | EVENT 고정 만료일 (SIGNUP=null) |
| validity_days | INT | Y | | SIGNUP 상대 유효기간(일) (EVENT=null) |
| issue_limit | INT | Y | CHECK (null 또는 >= 0) | 발급 한도 (null = 무제한) |
| issued_count | INT | N | DEFAULT 0, CHECK >= 0 | 누적 발급 수 |
| active | BOOLEAN | N | DEFAULT TRUE | 활성 여부 |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## kind 값

| 값 | 설명 |
|---|---|
| SIGNUP | 가입 축하 쿠폰 — 회원가입 시 자동 발급, validity_days 기반 상대 만료일 |
| EVENT | 이벤트 쿠폰 — 관리자 생성, valid_until 기반 고정 만료일, 선착순 issueLimit |

## discount_type 값

| 값 | 설명 |
|---|---|
| RATE | 퍼센트 할인 (discount_value = 1~100) |
| AMOUNT | 정액 할인 (discount_value = 원) |

## 인덱스

- `coupons_pkey` (`id`)

## 제약

- `chk_coupons_kind` CHECK `kind IN ('SIGNUP', 'EVENT')`
- `chk_coupons_discount_type` CHECK `discount_type IN ('RATE', 'AMOUNT')`
- `chk_coupons_issued_count` CHECK `issued_count >= 0`
- `chk_coupons_issue_limit` CHECK `issue_limit IS NULL OR issue_limit >= 0`

## 관계

- `coupons.id` ← `user_coupons.coupon_id` (1:N)

## 설계 메모

- **선착순 원자 카운트**: `UPDATE coupons SET issued_count = issued_count + 1 WHERE id = ? AND ... AND (issue_limit IS NULL OR issued_count < issue_limit)` — 업데이트 행 수 0이면 COUPON_SOLD_OUT
- **Seed**: 가입 축하 쿠폰(RATE 20%, 최소주문 5,000원, validity_days=30) 1건 마이그레이션 시 INSERT
- **SIGNUP 쿠폰 고유성**: `user_coupons.uq_user_coupons_customer_coupon (customer_id, coupon_id)` 로 1인 1회 DB 단 보장
