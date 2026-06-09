-- Phase 11: 이벤트 관리 — coupons 노출 기간 추가 + user_coupons 할인 스냅샷 추가
-- 의존: V20260608180000__create_coupons.sql (coupons / user_coupons 선행)

-- ───────────────────────────────────────────────────────────────────────────────
-- coupons: 이벤트 노출 기간 (EVENT 전용, SIGNUP=null)
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE coupons ADD COLUMN display_start_at DATE;
ALTER TABLE coupons ADD COLUMN display_end_at   DATE;

-- ───────────────────────────────────────────────────────────────────────────────
-- user_coupons: 발급 시 할인 스냅샷 (소급 방지)
-- ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE user_coupons ADD COLUMN discount_type  VARCHAR(20);
ALTER TABLE user_coupons ADD COLUMN discount_value INT;
ALTER TABLE user_coupons ADD COLUMN min_order      INT;

-- 기존 발급분 백필 — 마스터(coupons) 값 복사
UPDATE user_coupons uc
   SET discount_type  = c.discount_type,
       discount_value = c.discount_value,
       min_order      = c.min_order
  FROM coupons c
 WHERE uc.coupon_id = c.id;

-- NOT NULL 승격
ALTER TABLE user_coupons ALTER COLUMN discount_type  SET NOT NULL;
ALTER TABLE user_coupons ALTER COLUMN discount_value SET NOT NULL;
ALTER TABLE user_coupons ALTER COLUMN min_order      SET NOT NULL;

-- 스냅샷 CHECK 제약
ALTER TABLE user_coupons
    ADD CONSTRAINT chk_user_coupons_discount_type
        CHECK (discount_type IN ('RATE', 'AMOUNT'));
