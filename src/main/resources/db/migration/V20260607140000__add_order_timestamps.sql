-- Phase 5B step1: 주문 상태 전이 타임스탬프 컬럼 추가
ALTER TABLE orders ADD COLUMN accepted_at  TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN ready_at     TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN rejected_at  TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN cancelled_at TIMESTAMPTZ;
