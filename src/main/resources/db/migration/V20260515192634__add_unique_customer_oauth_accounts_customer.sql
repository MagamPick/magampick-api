ALTER TABLE customer_oauth_accounts
    ADD CONSTRAINT uq_customer_oauth_accounts_customer UNIQUE (customer_id);
