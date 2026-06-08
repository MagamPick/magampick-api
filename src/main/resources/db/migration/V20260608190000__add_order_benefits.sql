-- Phase 7: orders 테이블에 쿠폰/포인트 혜택 컬럼 추가
-- 의존: V20260608180000__create_coupons.sql (user_coupons 선행)

ALTER TABLE orders ADD COLUMN coupon_discount DECIMAL(12,0);
ALTER TABLE orders ADD COLUMN point_used      BIGINT;
ALTER TABLE orders ADD COLUMN earned_points   BIGINT;
ALTER TABLE orders ADD COLUMN final_amount    DECIMAL(12,0);
ALTER TABLE orders ADD COLUMN user_coupon_id  BIGINT REFERENCES user_coupons (id);

-- 기존 주문 백필: 혜택 미적용 = final_amount = total_price
UPDATE orders SET coupon_discount = 0, point_used = 0, final_amount = total_price WHERE final_amount IS NULL;
