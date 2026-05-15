# customer_oauth_accounts

소비자 소셜 계정 연결 테이블. 이번 이슈에서는 카카오 Mock 로그인만 다룬다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | row 식별자 |
| customer_id | BIGINT | N | FK | 연결된 소비자 ID |
| provider | VARCHAR(20) | N | CHECK | `KAKAO` |
| provider_user_id | VARCHAR(255) | N | UNIQUE(provider와 복합) | provider 내부 사용자 ID |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `customer_oauth_accounts_pkey` (`id`)
- `uq_customer_oauth_accounts_provider_user` UNIQUE (`provider`, `provider_user_id`)

## 제약

- `fk_customer_oauth_accounts_customer`  
  `customer_id -> customers.id`
- `chk_customer_oauth_accounts_provider`  
  `provider IN ('KAKAO')`
