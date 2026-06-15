ALTER TABLE point_accruals ADD COLUMN expiry_alert_sent_at TIMESTAMPTZ;
ALTER TABLE user_coupons ADD COLUMN expiry_alert_sent_at TIMESTAMPTZ;
