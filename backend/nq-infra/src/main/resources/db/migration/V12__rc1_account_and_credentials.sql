-- V12__rc1_account_and_credentials.sql
-- RC1 Phase A: introduce exchange account and credential model.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO roles (role_code, description, created_at)
SELECT 'ADMIN', 'System administrator', NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_code = 'ADMIN');

INSERT INTO roles (role_code, description, created_at)
SELECT 'OPERATOR', 'Operations user', NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_code = 'OPERATOR');

INSERT INTO roles (role_code, description, created_at)
SELECT 'VIEWER', 'Read-only user', NOW()
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_code = 'VIEWER');

INSERT INTO users (username, password_hash, enabled, created_at, updated_at)
SELECT 'system-migrated', crypt(gen_random_uuid()::text, gen_salt('bf')), FALSE, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'system-migrated');

CREATE TABLE IF NOT EXISTS exchange_accounts (
    exchange_account_id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    exchange_code VARCHAR(32) NOT NULL,
    trade_env VARCHAR(8) NOT NULL,
    account_alias VARCHAR(64) NOT NULL,
    external_account_ref VARCHAR(128),
    legacy_account_id BIGINT,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_exchange_accounts_owner_user FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT uq_exchange_accounts_legacy_account_id UNIQUE (legacy_account_id),
    CONSTRAINT chk_exchange_accounts_trade_env CHECK (trade_env IN ('SIM', 'LIVE')),
    CONSTRAINT chk_exchange_accounts_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_accounts_owner_exchange_env_alias
    ON exchange_accounts (owner_user_id, exchange_code, trade_env, account_alias);

CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_accounts_exchange_env_external_ref_not_null
    ON exchange_accounts (exchange_code, trade_env, external_account_ref)
    WHERE external_account_ref IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_accounts_default_scope
    ON exchange_accounts (owner_user_id, exchange_code, trade_env)
    WHERE is_default = TRUE;

INSERT INTO exchange_accounts (
    owner_user_id,
    exchange_code,
    trade_env,
    account_alias,
    external_account_ref,
    legacy_account_id,
    is_default,
    status,
    created_at,
    updated_at
)
SELECT
    migrated_user.id,
    UPPER(a.venue),
    'SIM',
    COALESCE(NULLIF(a.account_code, ''), 'legacy-' || a.account_id::text),
    NULL,
    a.account_id,
    FALSE,
    CASE WHEN UPPER(a.status) = 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END,
    a.created_at,
    NOW()
FROM accounts a
CROSS JOIN LATERAL (
    SELECT id
    FROM users
    WHERE username = 'system-migrated'
) migrated_user
WHERE NOT EXISTS (
    SELECT 1
    FROM exchange_accounts ea
    WHERE ea.legacy_account_id = a.account_id
);

CREATE TABLE IF NOT EXISTS exchange_account_credentials (
    credential_id BIGSERIAL PRIMARY KEY,
    exchange_account_id BIGINT NOT NULL,
    credential_type VARCHAR(32) NOT NULL,
    encrypted_payload BYTEA NOT NULL,
    key_version INTEGER NOT NULL,
    cipher_suite VARCHAR(32) NOT NULL DEFAULT 'PGP_SYM_AES256',
    masked_access_key VARCHAR(64),
    verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at TIMESTAMPTZ,
    rotated_from_credential_id BIGINT,
    last_verified_at TIMESTAMPTZ,
    last_verification_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_exchange_account_credentials_account
        FOREIGN KEY (exchange_account_id) REFERENCES exchange_accounts (exchange_account_id),
    CONSTRAINT fk_exchange_account_credentials_rotated_from
        FOREIGN KEY (rotated_from_credential_id) REFERENCES exchange_account_credentials (credential_id),
    CONSTRAINT chk_exchange_account_credentials_type
        CHECK (credential_type IN ('OKX_API_V5', 'BINANCE_HMAC', 'BINANCE_ED25519')),
    CONSTRAINT chk_exchange_account_credentials_status
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED', 'REVOKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_account_credentials_active_type
    ON exchange_account_credentials (exchange_account_id, credential_type)
    WHERE is_active = TRUE;
