-- 알림 설정 테이블 생성(V20260608200000) 이전 가입자 backfill
INSERT INTO customer_notification_settings (customer_id, nearby_deal, favorite_store, order_refund, review_reply, event_benefit, marketing, created_at, updated_at)
SELECT id, TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, NOW(), NOW()
FROM customers
WHERE id NOT IN (SELECT customer_id FROM customer_notification_settings);

INSERT INTO seller_notification_settings (seller_id, new_order, order_cancel, refund_request, new_review, notice, marketing, created_at, updated_at)
SELECT id, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE, NOW(), NOW()
FROM sellers
WHERE id NOT IN (SELECT seller_id FROM seller_notification_settings);
