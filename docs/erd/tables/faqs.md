# faqs

자주 묻는 질문(FAQ) 테이블. `audience` (CUSTOMER / SELLER) 별로 분리된 목록을 `sort_order` 순으로 제공한다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | FAQ 식별자 |
| audience | VARCHAR(20) | N | CHECK ('CUSTOMER','SELLER') | 대상 역할 |
| question | VARCHAR(200) | N | | 질문 텍스트 |
| answer | TEXT | N | | 답변 텍스트 |
| sort_order | INT | N | DEFAULT 0 | 표시 순서 (0부터, 오름차순 정렬) |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## audience 값

| 값 | 엔드포인트 | 설명 |
|---|---|---|
| CUSTOMER | `GET /api/v1/faqs` | 소비자 대상 FAQ |
| SELLER | `GET /api/v1/seller/faqs` | 사장 대상 FAQ |

## 인덱스

| 인덱스명 | 컬럼 | 용도 |
|---|---|---|
| `faqs_pkey` | `id` | PK |
| `idx_faqs_audience_sort` | `(audience, sort_order)` | audience 필터 + sortOrder 정렬 |

## 제약

- `chk_faqs_audience` CHECK `audience IN ('CUSTOMER', 'SELLER')`

## 관계

- 독립 테이블 — 다른 도메인과 FK 관계 없음

## 정렬 규칙

목록 조회: **audience 필터 → sort_order ASC**

## 설계 메모

- **관리자 FAQ 편집 API**: 현 단계 Out of Scope. seed 데이터로만 운영.
- **Seed**: 소비자 6건 + 사장 6건 = 12건 (마이그레이션 INSERT, sort_order 0~5).
