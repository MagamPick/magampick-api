# push_tokens

FCM 푸시 디바이스 토큰. 소비자/사장의 PWA 디바이스가 발급받은 FCM 토큰을 보관 — 발송 시 소유자별로 조회한다.

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `owner_type` | `VARCHAR(20)` | NOT NULL, CHECK IN (CUSTOMER, SELLER) | 토큰 소유자 종류 (polymorphic) |
| `owner_id` | `BIGINT` | NOT NULL | 소유자 ID — `owner_type` 에 따라 `customers.id` 또는 `sellers.id` |
| `token` | `VARCHAR(512)` | NOT NULL, UNIQUE | FCM 디바이스 토큰 |
| `platform` | `VARCHAR(20)` | NOT NULL, CHECK IN (WEB) | 디바이스 플랫폼 (현재 PWA=WEB 만) |
| `created_at` | `TIMESTAMP` | NOT NULL | 등록 시각 (`BaseEntity`) |
| `updated_at` | `TIMESTAMP` | NOT NULL | 갱신 시각 — 소유자 재할당 시 갱신 (`BaseEntity`) |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `uk_push_tokens_token` | `token` | 같은 토큰 1행 보장 — upsert 키 |
| `chk_push_tokens_owner_type` | `owner_type` | CUSTOMER/SELLER 만 허용 (admin 웹은 FCM 미사용) |
| `chk_push_tokens_platform` | `platform` | WEB 만 허용 (플랫폼 확장 시 CHECK 갱신) |
| `idx_push_tokens_owner` | `(owner_type, owner_id)` | 소유자별 토큰 조회 (발송 lookup) |

## 관계

- **FK 없음 (의도)** — `(owner_type, owner_id)` polymorphic 참조. 소비자/사장이 공유 `users` 테이블 없이 별도 테이블이라 단일 FK 불가. 무결성은 `owner_type` CHECK + 애플리케이션 레이어가 담당.

## 비즈니스 규칙

- **등록 = upsert** — 같은 `token` 재등록 시 새 행을 만들지 않고 소유자(`owner_type`, `owner_id`)를 재할당 (기기 공유·재로그인 대응).
- **해제 = hard delete** — 로그아웃·만료 토큰은 행을 삭제 (soft delete 안 함 — 죽은 토큰은 조회에서 즉시 제외돼야 함).
- **죽은 토큰 자동 정리** — 발송 시 FCM 이 `UNREGISTERED`/`INVALID_ARGUMENT` 를 반환하면 해당 토큰 행을 삭제한다.
