# customer_terms_agreements

소비자-약관 동의 기록. 가입 시점에 동의한 약관(특정 `type+version` row)을 `term_id` 참조로 기록한다. 조인 테이블 (surrogate PK).

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `customer_id` | `BIGINT` | NOT NULL, FK → `customers.id` ON DELETE CASCADE | 동의한 소비자 |
| `term_id` | `BIGINT` | NOT NULL, FK → `terms.id` | 동의한 약관 (특정 type+version row) |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | 동의 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `uk_customer_terms_agreements_customer_term` | `(customer_id, term_id)` | 중복 동의 차단 + 소비자별 조회 index |

`(customer_id, term_id)` UNIQUE 인덱스가 선두 컬럼 `customer_id` prefix로 `WHERE customer_id = ?` 쿼리를 커버하므로 별도 인덱스 불필요.

## 관계

- `customers` 1:N `customer_terms_agreements` — 한 소비자가 여러 약관 동의
- `terms` 1:N `customer_terms_agreements` — 한 약관(row)에 여러 소비자 동의
- `term_id`가 특정 `(type, version)` row를 가리키므로 동의한 약관 버전이 스냅샷된다

## 비즈니스 규칙

- 가입 트랜잭션에서 필수 약관(`required=true`) 전체 + 동의한 선택 약관을 기록 (Step 3 — 가입 오케스트레이션).
- 동의 시각 = `created_at` (row 생성 시점). 불변 — 동의 후 수정하지 않는다.
- 마케팅(`MARKETING`) 동의 시 광고성 알림 초기값 ON 매핑은 알림 설정 도메인 머지 후 별도 연동 (이번 범위 X).
