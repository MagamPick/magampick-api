ALTER TABLE clearance_items
    ADD COLUMN close_reason VARCHAR(30)
        CONSTRAINT chk_clearance_items_close_reason CHECK (close_reason IN ('EXPIRED', 'SOLD_OUT', 'MANUAL'));
