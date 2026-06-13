-- D1: 포인트 적립 예정→확정 설계 변경
--   earned_at / expires_at — PENDING lot 생성 시 미결정, confirm 시점에 셋
--   status CHECK — PENDING 추가

ALTER TABLE point_accruals ALTER COLUMN earned_at DROP NOT NULL;
ALTER TABLE point_accruals ALTER COLUMN expires_at DROP NOT NULL;

ALTER TABLE point_accruals DROP CONSTRAINT chk_point_accruals_status;
ALTER TABLE point_accruals ADD CONSTRAINT chk_point_accruals_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'EXHAUSTED', 'EXPIRED'));
