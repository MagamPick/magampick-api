# terms

약관 마스터 (서비스 이용·개인정보·위치·연령·마케팅). `(type, version)` 별 1 row, body 보관. 회원가입 화면이 조회해 표시한다.

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `type` | `VARCHAR(30)` | NOT NULL, CHECK | 약관 종류 (`TermType` enum) |
| `version` | `INT` | NOT NULL, CHECK > 0 | 약관 버전 (1부터) |
| `title` | `VARCHAR(200)` | NOT NULL | 약관 제목 |
| `body` | `TEXT` | NOT NULL | 약관 본문 |
| `required` | `BOOLEAN` | NOT NULL | 필수 동의 여부 (TRUE=필수) |
| `created_at` | `TIMESTAMP` | NOT NULL | 생성 시각 |
| `updated_at` | `TIMESTAMP` | NOT NULL | 수정 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `chk_terms_type` | `type` | enum 값 강제 (TERMS_OF_SERVICE / PRIVACY / LOCATION / AGE_14 / MARKETING) |
| `chk_terms_version_positive` | `version` | version > 0 |
| `uk_terms_type_version` | `(type, version)` | type별 버전 중복 차단 + `type` prefix로 type 조회 커버 |

## TermType (enum, VARCHAR + CHECK)

| 값 | 의미 | required |
|---|---|---|
| `TERMS_OF_SERVICE` | 서비스 이용약관 | ✓ |
| `PRIVACY` | 개인정보 수집·이용 동의 | ✓ |
| `LOCATION` | 위치 기반 서비스 이용약관 | ✓ |
| `AGE_14` | 만 14세 이상 확인 (개인정보보호법 제22조의2) | ✓ |
| `MARKETING` | 마케팅 정보 수신 동의 | (선택) |

## 비즈니스 규칙 / 운영 메모

- 초기 약관 5종(version 1)은 마이그레이션 seed. body는 placeholder — 운영자가 SQL로 실제 약관 본문 갱신 (관리 UI는 백로그).
- 약관 변경 = 새 version row 추가 (기존 row 보존, 이력 추적). MVP는 type당 1 row.
- 가입 화면 조회: `GET /api/v1/terms` (public). `findAllByOrderByTypeAsc`.
- 동의 기록은 [`customer_terms_agreements`](customer_terms_agreements.md) 참조.
