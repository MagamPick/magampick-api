-- orders status CHECK 제약에 AWAITING_PAYMENT 추가
ALTER TABLE orders DROP CONSTRAINT chk_orders_status;

ALTER TABLE orders ADD CONSTRAINT chk_orders_status
    CHECK (status IN (
        'AWAITING_PAYMENT',
        'PENDING',
        'PREPARING',
        'READY',
        'COMPLETED',
        'NO_SHOW',
        'REJECTED',
        'CANCELLED'
    ));
