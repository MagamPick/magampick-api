# admins

관리자 계정 테이블.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 관리자 식별자 |
| email | VARCHAR(255) | N | UNIQUE | 로그인 이메일 |
| password_hash | VARCHAR(60) | N |  | BCrypt 해시 |
| name | VARCHAR(20) | N |  | 관리자 이름 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `admins_pkey` (`id`)
- `admins_email_key` UNIQUE (`email`)
