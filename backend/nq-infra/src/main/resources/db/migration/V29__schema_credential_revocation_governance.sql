-- Batch 5-B: credential revocation schema governance.
-- Scope is intentionally limited to exchange_account_credentials and credential_audit_logs.
-- This migration adds lifecycle, permission, usage, failure and append-only audit metadata only.

ALTER TABLE exchange_account_credentials
    ADD COLUMN credential_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN revoked_by VARCHAR(128),
    ADD COLUMN revoke_reason TEXT,
    ADD COLUMN rotated_at TIMESTAMPTZ,
    ADD COLUMN rotated_by VARCHAR(128),
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN failed_auth_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN permission_scope VARCHAR(64),
    ADD COLUMN withdraw_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ip_allowlist_required BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN external_secret_ref VARCHAR(256),
    ADD COLUMN key_alias VARCHAR(128);

UPDATE exchange_account_credentials current_credential
SET credential_status = CASE
        WHEN current_credential.is_active = TRUE
             AND current_credential.verification_status <> 'REVOKED' THEN 'ACTIVE'
        WHEN current_credential.verification_status = 'REVOKED'
             OR current_credential.is_active = FALSE THEN 'ROTATED'
        ELSE 'DISABLED'
    END,
    rotated_at = CASE
        WHEN current_credential.verification_status = 'REVOKED'
             OR current_credential.is_active = FALSE THEN COALESCE(
                 current_credential.rotated_at,
                 current_credential.revoked_at,
                 current_credential.updated_at
             )
        ELSE current_credential.rotated_at
    END
WHERE current_credential.credential_status = 'ACTIVE';

ALTER TABLE exchange_account_credentials
    ADD CONSTRAINT chk_exchange_account_credentials_credential_status
        CHECK (credential_status IN ('ACTIVE', 'DISABLED', 'REVOKED', 'EXPIRED', 'ROTATED')),
    ADD CONSTRAINT chk_exchange_account_credentials_failed_auth_count
        CHECK (failed_auth_count >= 0),
    ADD CONSTRAINT chk_exchange_account_credentials_permission_scope
        CHECK (permission_scope IS NULL OR permission_scope IN ('READ_ONLY', 'TRADE')),
    ADD CONSTRAINT chk_exchange_account_credentials_revoked_at_required
        CHECK (credential_status <> 'REVOKED' OR revoked_at IS NOT NULL);

COMMENT ON COLUMN exchange_account_credentials.credential_status IS '凭证生命周期状态，允许值：ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED；独立于 verification_status，表示凭证是否可用以及不可用原因。';
COMMENT ON COLUMN exchange_account_credentials.revoked_by IS '凭证不可恢复撤销操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。';
COMMENT ON COLUMN exchange_account_credentials.revoke_reason IS '凭证不可恢复撤销原因，可为空；用于安全审计和复盘，不得保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。';
COMMENT ON COLUMN exchange_account_credentials.rotated_at IS '凭证被新版本替换的时间，可为空；用于区分 ROTATED 生命周期与不可恢复 REVOKED 撤销。';
COMMENT ON COLUMN exchange_account_credentials.rotated_by IS '凭证轮换操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。';
COMMENT ON COLUMN exchange_account_credentials.last_used_at IS '凭证最近一次被服务端业务路径使用的时间，可为空；不表示交易所在线校验成功，也不保存请求、签名或凭证明文。';
COMMENT ON COLUMN exchange_account_credentials.failed_auth_count IS '凭证认证或权限校验失败累计次数，最小值为 0；仅记录计数，不保存交易所返回的敏感错误上下文。';
COMMENT ON COLUMN exchange_account_credentials.permission_scope IS '凭证权限范围，可为空；允许值为 READ_ONLY / TRADE，NULL 表示当前 schema-only 阶段尚未由代码确认权限，不代表允许提现或真实交易。';
COMMENT ON COLUMN exchange_account_credentials.withdraw_enabled IS '凭证是否允许提现，默认 false；本字段只保存治理元数据，不代表系统实现提现能力或开启 LIVE trading。';
COMMENT ON COLUMN exchange_account_credentials.ip_allowlist_required IS '凭证是否要求交易所侧 IP allowlist，默认 true；只记录治理要求，不保存 IP 凭证、token、cookie 或网络访问密钥。';
COMMENT ON COLUMN exchange_account_credentials.external_secret_ref IS '外部密钥系统引用，可为空；只允许保存外部 Secret Manager / KMS 的引用标识，不得保存 secret、token、API key、private key、passphrase、cookie 或助记词。';
COMMENT ON COLUMN exchange_account_credentials.key_alias IS '密钥别名，可为空；只允许保存脱敏别名或外部密钥别名，不得保存 secret、token、API key、private key、passphrase、cookie 或助记词。';

CREATE TABLE credential_audit_logs (
    credential_audit_log_id BIGSERIAL PRIMARY KEY,
    credential_id BIGINT NOT NULL,
    exchange_account_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(128),
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_credential_audit_logs_credential
        FOREIGN KEY (credential_id) REFERENCES exchange_account_credentials (credential_id),
    CONSTRAINT fk_credential_audit_logs_exchange_account
        FOREIGN KEY (exchange_account_id) REFERENCES exchange_accounts (exchange_account_id),
    CONSTRAINT chk_credential_audit_logs_event_type
        CHECK (event_type IN (
            'CREATED',
            'VERIFIED',
            'FAILED_VERIFICATION',
            'DISABLED',
            'REVOKED',
            'ROTATED',
            'EXPIRED',
            'USED',
            'ACCESS_DENIED'
        ))
);

CREATE INDEX idx_credential_audit_logs_credential_created
    ON credential_audit_logs (credential_id, created_at DESC);

CREATE INDEX idx_credential_audit_logs_account_created
    ON credential_audit_logs (exchange_account_id, created_at DESC);

CREATE INDEX idx_credential_audit_logs_event_created
    ON credential_audit_logs (event_type, created_at DESC);

COMMENT ON TABLE credential_audit_logs IS '账户凭证 append-only 审计日志表。用于记录创建、校验、禁用、撤销、轮换、过期、使用和拒绝访问事件；不得 hard delete，不保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。';
COMMENT ON COLUMN credential_audit_logs.credential_audit_log_id IS '凭证审计日志主键。';
COMMENT ON COLUMN credential_audit_logs.credential_id IS '关联的账户凭证主键，引用 exchange_account_credentials.credential_id；凭证记录不得 hard delete。';
COMMENT ON COLUMN credential_audit_logs.exchange_account_id IS '关联的交易账户主键，引用 exchange_accounts.exchange_account_id，用于按账户追溯 credential 审计事件。';
COMMENT ON COLUMN credential_audit_logs.event_type IS '凭证审计事件类型，允许值：CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED。';
COMMENT ON COLUMN credential_audit_logs.actor IS '事件操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。';
COMMENT ON COLUMN credential_audit_logs.reason IS '事件原因或摘要，可为空；用于审计复盘，不得保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。';
COMMENT ON COLUMN credential_audit_logs.metadata IS '事件脱敏元数据，默认空 JSONB；只允许保存状态、结果码、request id、策略判断等审计上下文，不得保存密钥、token、API secret、私钥、助记词、cookie、passphrase、签名、明文 payload 或交易所凭证。';
COMMENT ON COLUMN credential_audit_logs.created_at IS '凭证审计事件创建时间；append-only 记录的业务时间以新增日志为准。';
