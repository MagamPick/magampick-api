# announcements

단일 글로벌 공지사항 테이블. 소비자·사장님 구분 없이 하나의 목록으로 제공된다.
NOTICE(공지) / EVENT(이벤트) / UPDATE(업데이트) 세 가지 태그로 분류한다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 공지사항 식별자 |
| tag | VARCHAR(20) | N | CHECK ('NOTICE','EVENT','UPDATE') | 공지 태그 |
| pinned | BOOLEAN | N | DEFAULT FALSE | 핀 고정 여부 (목록 최상단 표시) |
| title | VARCHAR(200) | N | | 공지 제목 |
| body | TEXT | N | | 공지 본문 |
| published_at | DATE | N | | 발행일 (FE 계약의 `date` 필드) |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## tag 값

| 값 | JSON 직렬화 | 설명 |
|---|---|---|
| NOTICE | "notice" | 공지 — 점검·정책 변경 등 |
| EVENT | "event" | 이벤트 — 프로모션·쿠폰 이벤트 등 |
| UPDATE | "update" | 업데이트 — 기능 변경·개선 안내 등 |

## 인덱스

| 인덱스명 | 컬럼 | 용도 |
|---|---|---|
| `announcements_pkey` | `id` | PK |
| `idx_announcements_sort` | `(pinned DESC, published_at DESC, id DESC)` | 목록 정렬 조회 최적화 |

## 제약

- `chk_announcements_tag` CHECK `tag IN ('NOTICE', 'EVENT', 'UPDATE')`

## 관계

- 독립 테이블 — 다른 도메인과 FK 관계 없음

## 정렬 규칙

목록 조회 기본 정렬: **pinned DESC → published_at DESC → id DESC**

1. 핀된 공지(`pinned = TRUE`)가 최상단
2. 동일 핀 여부 내에서 발행일 최신순
3. 동일 발행일이면 id 내림차순 (나중에 생성된 것이 앞)

## 설계 메모

- **audience 세분화 없음**: 소비자/사장님 구분 없는 단일 글로벌 목록. 향후 필요 시 `audience` 컬럼 추가.
- **발행 시각 아닌 발행일**: `published_at` 은 DATE 타입. 시각 단위 예약 발행은 현 단계 Out of Scope.
- **publishedAt = 생성일**: API 생성 시 `LocalDate.now(clock)` 으로 자동 설정. FE 계약 필드명 `date` 매핑.
- **Seed**: 11건 초기 데이터 (마이그레이션 INSERT).
