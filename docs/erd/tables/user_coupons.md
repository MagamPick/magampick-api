# user_coupons

소비자에게 발급된 쿠폰 인스턴스 테이블. 유효기간(expires_at)은 발급 시점에 스냅샷으로 저장된다.
(EVENT = 마스터 valid_until 복사, SIGNUP = 가입일 + validity_days)

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 발급 인스턴스 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 쿠폰 보유 소비자 |
| coupon_id | BIGINT | N | FK → coupons.id | 원본 쿠폰 마스터 |
| status | VARCHAR(20) | N | DEFAULT 'USABLE', CHECK (3값) | 쿠폰 상태 |
| expires_at | DATE | N | | 유효기간 만료일 (발급 시 스냅샷) |
| issued_at | TIMESTAMP | N | | 발급 시각 |
| used_at | TIMESTAMP | Y | | 사용 시각 (미사용=null) |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## status 값

| 값 | 설명 |
|---|---|
| USABLE | 사용 가능 (만료일 경과 여부는 조회 시 방어판정으로 EXPIRED 표시) |
| USED | 사용 완료 (PR3에서 구현) |
| EXPIRED | 만료 소멸 (PR4 배치에서 일괄 갱신) |

## 인덱스

- `user_coupons_pkey` (`id`)
- `idx_user_coupons_customer` (`customer_id`)

## 제약

- `uq_user_coupons_customer_coupon` UNIQUE (`customer_id`, `coupon_id`) — 1인 1회 발급 보장
- `chk_user_coupons_status` CHECK `status IN ('USABLE', 'USED', 'EXPIRED')`

## 관계

- `user_coupons.customer_id` → `customers.id` (N:1)
- `user_coupons.coupon_id` → `coupons.id` (N:1)

## 설계 메모

- **만료 방어판정**: PR2 조회 시 `status == USABLE && expiresAt < today` → 응답에 EXPIRED 표시. DB 실제 갱신은 PR4 소멸 배치
- **사용 흐름**: PR3(사용)에서 `status = USED`, `used_at = now` 로 변경
- **1인 1회**: UNIQUE 제약으로 DB 단 보장 (서비스 레이어 existsBy + 제약 이중 방어)
