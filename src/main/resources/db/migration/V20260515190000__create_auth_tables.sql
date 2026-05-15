CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60),
    nickname VARCHAR(20) NOT NULL,
    phone VARCHAR(20),
    phone_verified_at TIMESTAMP,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sellers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    owner_name VARCHAR(20) NOT NULL,
    business_number VARCHAR(10) NOT NULL,
    phone VARCHAR(20),
    phone_verified_at TIMESTAMP,
    verification_status VARCHAR(20) NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_sellers_verification_status
        CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE TABLE admins (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    name VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    owner_role VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_refresh_tokens_owner_role
        CHECK (owner_role IN ('CUSTOMER', 'SELLER', 'ADMIN'))
);

CREATE TABLE customer_oauth_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_oauth_accounts_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT uq_customer_oauth_accounts_provider_user
        UNIQUE (provider, provider_user_id),
    CONSTRAINT chk_customer_oauth_accounts_provider
        CHECK (provider IN ('KAKAO'))
);

CREATE INDEX idx_refresh_tokens_owner ON refresh_tokens(owner_role, owner_id);
