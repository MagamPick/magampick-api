# point_accruals

포인트 적립 lot 테이블. FIFO 차감 방식으로 잔량(remaining_amount)을 관리하는 balance source of truth.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 적립 lot 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 포인트 소유 소비자 |
| order_id | BIGINT | Y | FK → orders.id | 적립 출처 주문 (비주문 적립 = null) |
| initial_amount | BIGINT | N | | 최초 적립 포인트 (1P = 1원) |
| remaining_amount | BIGINT | N | CHECK (0 이상 initial_amount 이하) | FIFO 차감 후 잔량 |
| earned_at | TIMESTAMP | N | | 적립 발생 시각 |
| expires_at | TIMESTAMP | N | | 유효기간 만료 시각 (earned_at + 1년) |
| status | VARCHAR(20) | N | DEFAULT 'ACTIVE', CHECK (3값) | 적립 lot 상태 (아래 참조) |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## status 값

| 값 | 설명 |
|---|---|
| ACTIVE | 잔여 포인트 있음 |
| EXHAUSTED | FIFO 차감으로 완전 소진 |
| EXPIRED | 유효기간 만료 소멸 |

## 인덱스

- `point_accruals_pkey` (`id`)
- `idx_point_accruals_customer_status_earned` (`customer_id`, `status`, `earned_at`)

## 제약

- `chk_point_accruals_status` CHECK `status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED')`
- `chk_point_accruals_remaining` CHECK `remaining_amount >= 0 AND remaining_amount <= initial_amount`

## 관계

- `point_accruals.customer_id` → `customers.id` (N:1)
- `point_accruals.order_id` → `orders.id` (N:1, nullable)

## 설계 메모

- **FIFO 차감**: 사용 시 earned_at 오름차순으로 오래된 lot 부터 차감 (Phase 7+ write PR 에서 구현)
- **잔액 쿼리**: `SUM(remaining_amount) WHERE status = 'ACTIVE'` 로 전체 잔액 계산
- **1P = 1원**: 정수 단위 운영. 소수점 없음
