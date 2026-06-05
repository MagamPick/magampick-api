# seller_terms_agreements

사장-약관 동의 기록. 가입 시점에 동의한 약관(특정 `type+version` row)을 `term_id` 참조로 기록한다. 조인 테이블 (surrogate PK).

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `seller_id` | `BIGINT` | NOT NULL, FK -> `sellers.id` ON DELETE CASCADE | 동의한 사장 |
| `term_id` | `BIGINT` | NOT NULL, FK -> `terms.id` | 동의한 약관 (특정 type+version row) |
| `created_at` | `TIMESTAMP` | NOT NULL, DEFAULT NOW() | 동의 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `uk_seller_terms_agreements_seller_term` | `(seller_id, term_id)` | 중복 동의 차단 + 사장별 조회 index |

`(seller_id, term_id)` UNIQUE 인덱스가 선두 컬럼 `seller_id` prefix로 `WHERE seller_id = ?` 쿼리를 커버하므로 별도 인덱스 불필요.

## 관계

- `sellers` 1:N `seller_terms_agreements` — 한 사장이 여러 약관 동의
- `terms` 1:N `seller_terms_agreements` — 한 약관(row)에 여러 사장 동의
- `term_id`가 특정 `(type, version)` row를 가리키므로 동의한 약관 버전이 스냅샷된다

## 비즈니스 규칙

- 사장 가입 트랜잭션에서 필수 약관(`TERMS_OF_SERVICE`, `PRIVACY`, `LOCATION`, `AGE_19`) 전체 + 동의한 선택 약관을 기록한다.
- `AGE_19`는 사장 가입 전용 자기 신고 연령 확인이다. 소비자 가입은 `AGE_14`를 사용한다.
- 동의 시각 = `created_at` (row 생성 시점). 불변 — 동의 후 수정하지 않는다.
