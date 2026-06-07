# payments

주문 결제 내역 테이블. Phase 5A: orders 와 1:1. Stub 자동 승인 → 실제 Toss Sandbox 연동은 Phase 5B 이후.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 결제 식별자 |
| order_id | BIGINT | N | FK → orders.id, UNIQUE | 주문 1:1 관계. 중복 결제 방지 |
| provider | VARCHAR(10) | N | | 결제 제공사. 현재 고정 `"TOSS"` (대문자) |
| method | VARCHAR(20) | N | | 결제 수단 (예: `"toss"`) |
| payment_key | VARCHAR(200) | N | | PG 결제키. Stub = `"stub_{UUID}"` |
| amount | NUMERIC(12, 0) | N | CHECK amount > 0 | 결제 금액 |
| status | VARCHAR(20) | N | CHECK (3값) | 결제 상태 (아래 참조) |
| approved_at | TIMESTAMP | Y | | PG 승인 시각 (KST 기준) |
| created_at | TIMESTAMP | N | | 생성 시각 |
| updated_at | TIMESTAMP | N | | 수정 시각 |

## status 값 (3값)

| 값 | 설명 |
|---|---|
| APPROVED | 결제 승인 완료 |
| FAILED | 결제 실패 (Stub = 미사용, 실 PG 연동 후 사용) |
| CANCELED | 결제 취소 (환불. Phase 6) |

## 인덱스

- `payments_pkey` (`id`)
- `payments_order_id_key` UNIQUE (`order_id`)
- `idx_payments_order_id` (`order_id`)

## 제약

- `fk_payments_order` FK `order_id → orders.id`
- `uq_payments_order_id` UNIQUE `order_id` (중복 결제 방지)
- `chk_payments_status` CHECK `status IN ('APPROVED','FAILED','CANCELED')`
- `chk_payments_amount_positive` CHECK `amount > 0`

## 관계

- `payments.order_id` → `orders.id` (N:1, UNIQUE → 사실상 1:1)

## 설계 메모

- **도메인 패키지**: `com.magampick.payment` (order 도메인과 분리)
- **Stub 패턴**: `PaymentGateway` 인터페이스 + `StubPaymentGateway` 구현체. 실 Toss 연동 시 `TossPaymentGateway` 추가 후 `@Profile` 로 분기
- **payment_key NOT NULL**: Phase 5A 는 항상 APPROVED+키 존재. Phase 5B 에서 FAILED 도입 시 nullable 완화 및 FAILED 처리 검토 예정
- **approved_at**: PG 로부터 받은 승인 시각. Stub = 서버 현재 시각(KST)
- **CANCELED 상태**: 환불 처리 시 (Phase 6). 별도 컬럼(refund_amount 등) 추가 예정
- **idempotency**: `payment_key` + `uq_payments_order_id` 로 멱등성 보장 (Phase 5A: Stub 단순화)
