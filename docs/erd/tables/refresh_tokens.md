# refresh_tokens

Refresh Token 저장 테이블. 토큰 원문은 저장하지 않고 SHA-256 해시를 저장한다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | row 식별자 |
| owner_id | BIGINT | N |  | 사용자 ID |
| owner_role | VARCHAR(20) | N | CHECK | `CUSTOMER`, `SELLER`, `ADMIN` |
| token_hash | VARCHAR(64) | N | UNIQUE | refresh token SHA-256 해시 |
| expires_at | TIMESTAMP | N |  | 만료 시각 |
| revoked_at | TIMESTAMP | Y |  | 무효화 시각 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `refresh_tokens_pkey` (`id`)
- `refresh_tokens_token_hash_key` UNIQUE (`token_hash`)
- `idx_refresh_tokens_owner` (`owner_role`, `owner_id`)

## 제약

- `chk_refresh_tokens_owner_role`  
  `owner_role IN ('CUSTOMER', 'SELLER', 'ADMIN')`

## 관계

- Polymorphic 관계(`owner_role`, `owner_id`)라 DB FK는 사용하지 않음
