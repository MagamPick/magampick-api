# customers

소비자 계정 테이블.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 소비자 식별자 |
| email | VARCHAR(255) | N | UNIQUE | 로그인 이메일 |
| password_hash | VARCHAR(60) | Y |  | BCrypt 해시. 소셜 전용 계정은 NULL 가능 |
| nickname | VARCHAR(20) | N |  | 닉네임 |
| phone | VARCHAR(20) | Y |  | 소비자 프로필에서 수정 가능. 변경 시 본인인증 stub 통과로 간주. |
| phone_verified_at | TIMESTAMP | Y |  | 휴대폰 변경 시 함께 갱신되는 본인인증 통과 시각. |
| deleted_at | TIMESTAMP | Y |  | 소프트 삭제 시각 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `customers_pkey` (`id`)
- `customers_email_key` UNIQUE (`email`)

## 관계

- `customer_oauth_accounts.customer_id -> customers.id` (1:N)
