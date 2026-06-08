-- Credential permission probe schema governance.
-- Scope is intentionally limited to exchange_account_credentials and credential_audit_logs.
-- This migration prepares metadata and append-only audit semantics for future permission probes;
-- it does not implement Java/API behavior and does not call any real exchange.

ALTER TABLE exchange_account_credentials
    ADD COLUMN permission_probe_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PROBED',
    ADD COLUMN last_permission_probe_at TIMESTAMPTZ,
    ADD COLUMN last_permission_probe_error TEXT,
    ADD COLUMN ip_allowlist_probe_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED';

ALTER TABLE exchange_account_credentials
    DROP CONSTRAINT chk_exchange_account_credentials_permission_scope;

ALTER TABLE exchange_account_credentials
    ADD CONSTRAINT chk_exchange_account_credentials_permission_scope
        CHECK (permission_scope IS NULL OR permission_scope IN ('READ_ONLY', 'TRADE', 'FUNDING')),
    ADD CONSTRAINT chk_exchange_account_credentials_permission_probe_status
        CHECK (permission_probe_status IN ('NOT_PROBED', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    ADD CONSTRAINT chk_exchange_account_credentials_ip_allowlist_probe_status
        CHECK (ip_allowlist_probe_status IN ('NOT_CHECKED', 'PASSED', 'FAILED', 'UNKNOWN', 'SKIPPED'));

COMMENT ON COLUMN exchange_account_credentials.permission_scope IS '凭证权限范围，可为空；允许值为 READ_ONLY / TRADE / FUNDING，NULL 表示尚未确认真实交易所权限，不代表允许交易、提现、LIVE 或 AI/DH 使用 credential。';
COMMENT ON COLUMN exchange_account_credentials.permission_probe_status IS '真实交易所权限探活状态，允许值：NOT_PROBED / IN_PROGRESS / SUCCEEDED / FAILED / SKIPPED；本字段仅为后续 schema 准备，不表示 permission probe 已实现或真实交易所权限可用。';
COMMENT ON COLUMN exchange_account_credentials.last_permission_probe_at IS '最近一次真实交易所权限探活完成时间，可为空；独立于 last_verified_at 结构性校验时间和 last_used_at 业务使用时间，不保存请求、签名或凭证明文。';
COMMENT ON COLUMN exchange_account_credentials.last_permission_probe_error IS '最近一次权限探活脱敏错误摘要或错误分类，可为空；不得保存 secret、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、headers、request body、raw response、明文 payload 或交易所凭证。';
COMMENT ON COLUMN exchange_account_credentials.ip_allowlist_probe_status IS '交易所侧 IP allowlist 探活状态，允许值：NOT_CHECKED / PASSED / FAILED / UNKNOWN / SKIPPED；只记录脱敏结果，不保存 IP 凭证、token、cookie、headers、签名、raw response 或网络访问密钥。';
COMMENT ON COLUMN exchange_account_credentials.withdraw_enabled IS '凭证是否允许提现，默认 false；本字段只保存治理元数据，不代表系统实现提现能力、开启 LIVE trading 或允许资金转移；本轮未在未确认现有数据前新增强制 false CHECK。';

ALTER TABLE credential_audit_logs
    DROP CONSTRAINT chk_credential_audit_logs_event_type;

ALTER TABLE credential_audit_logs
    ADD CONSTRAINT chk_credential_audit_logs_event_type
        CHECK (event_type IN (
            'CREATED',
            'VERIFIED',
            'FAILED_VERIFICATION',
            'DISABLED',
            'ENABLED',
            'REVOKED',
            'ROTATED',
            'EXPIRED',
            'USED',
            'ACCESS_DENIED',
            'PERMISSION_PROBE_STARTED',
            'PERMISSION_PROBE_SUCCEEDED',
            'PERMISSION_PROBE_FAILED',
            'PERMISSION_PROBE_SKIPPED'
        ));

COMMENT ON TABLE credential_audit_logs IS '账户凭证 append-only 审计日志表。用于记录创建、校验、禁用、重新启用、撤销、轮换、过期、使用、拒绝访问和权限探活事件；不得 hard delete，不保存密钥、token、API secret、私钥、助记词、cookie、passphrase、签名、request body、raw response、明文 payload 或交易所凭证。';
COMMENT ON COLUMN credential_audit_logs.event_type IS '凭证审计事件类型，允许值：CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / ENABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED / PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED；permission probe 事件仅表示未来权限探活审计语义已准备。';
COMMENT ON COLUMN credential_audit_logs.metadata IS '事件脱敏元数据，默认空 JSONB；只允许保存状态、结果码、request id、策略判断等审计上下文，不得保存密钥、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、headers、request body、raw response、明文 payload 或交易所凭证。';
