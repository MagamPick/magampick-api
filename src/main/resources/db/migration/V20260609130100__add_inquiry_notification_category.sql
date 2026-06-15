-- Phase 11: INQUIRY 알림 카테고리 추가 — notifications.chk_category 제약 갱신
-- 기존 머지된 마이그레이션(V20260608200000__create_notification_domain.sql) 은 수정하지 않고 제약 재선언.

ALTER TABLE notifications DROP CONSTRAINT chk_category;

ALTER TABLE notifications
    ADD CONSTRAINT chk_category
        CHECK (category IN ('DEAL','ORDER','REVIEW','BENEFIT','SYSTEM','REFUND','SETTLEMENT','NOTICE','INQUIRY'));
