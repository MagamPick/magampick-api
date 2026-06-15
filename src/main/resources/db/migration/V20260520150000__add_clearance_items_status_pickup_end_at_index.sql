CREATE INDEX idx_clearance_items_status_pickup_end_at
    ON clearance_items (status, pickup_end_at);
