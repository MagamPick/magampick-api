# point_transactions

포인트 내역 테이블. 적립·사용·만료·복원·회수 5사유를 기록한다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 내역 식별자 |
| customer_id | BIGINT | N | FK → customers.id | 포인트 소유 소비자 |
| order_id | BIGINT | Y | FK → orders.id | 연관 주문 (소멸 등 주문 무관 사유 = null) |
| reason | VARCHAR(20) | N | CHECK (5값) | 내역 사유 (아래 참조) |
| amount | BIGINT | N | CHECK (> 0) | 포인트 변동량 (항상 양수) |
| store_name | VARCHAR(100) | Y | | 연관 매장 이름 스냅샷 (주문 없는 내역 = null) |
| occurred_at | TIMESTAMP | N | | 내역 발생 시각 |
| created_at | TIMESTAMP | N | DEFAULT NOW() | 생성 시각 |
| updated_at | TIMESTAMP | N | DEFAULT NOW() | 수정 시각 |

## reason 값 (5종)

| 값 | 설명 |
|---|---|
| EARN | 구매 적립 |
| USE | 포인트 사용 |
| EXPIRE | 유효기간 만료 소멸 |
| RESTORE | 취소/환불로 인한 복원 |
| CLAWBACK | 비정상 거래 회수 |

## 인덱스

- `point_transactions_pkey` (`id`)
- `idx_point_transactions_customer_occurred` (`customer_id`, `occurred_at DESC`)

## 제약

- `chk_point_transactions_reason` CHECK `reason IN ('EARN', 'USE', 'EXPIRE', 'RESTORE', 'CLAWBACK')`
- `chk_point_transactions_amount` CHECK `amount > 0`

## 관계

- `point_transactions.customer_id` → `customers.id` (N:1)
- `point_transactions.order_id` → `orders.id` (N:1, nullable)

## 설계 메모

- **amount 양수**: 방향은 reason 으로 판별. 단일 컬럼으로 이력 집계 단순화
- **store_name 스냅샷**: orders 조인 없이 FE 표시용으로 캐싱
- **필터 그룹**: 적립(EARN, RESTORE) / 사용·소멸(USE, EXPIRE, CLAWBACK) 으로 FE 분류
