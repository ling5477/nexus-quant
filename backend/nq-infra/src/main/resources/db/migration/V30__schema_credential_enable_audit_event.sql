-- Batch 5-F-B: credential enable audit event schema governance.
-- Scope is intentionally limited to the credential_audit_logs event_type CHECK and comments.
-- This migration prepares append-only audit semantics for a future enable command; it does not implement API or Java behavior.

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
            'ACCESS_DENIED'
        ));

COMMENT ON TABLE credential_audit_logs IS '账户凭证 append-only 审计日志表。用于记录创建、校验、禁用、重新启用、撤销、轮换、过期、使用和拒绝访问事件；不得 hard delete，不保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。';
COMMENT ON COLUMN credential_audit_logs.event_type IS '凭证审计事件类型，允许值：CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / ENABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED；ENABLED 表示 DISABLED credential 经校验后重新启用。';
COMMENT ON COLUMN credential_audit_logs.metadata IS '事件脱敏元数据，默认空 JSONB；只允许保存状态、结果码、request id、策略判断等审计上下文，不得保存密钥、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、明文 payload 或交易所凭证。';
