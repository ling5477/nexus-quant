-- 只新增稳定操作证据及有界历史索引，不改写凭据密文、版本、生命周期或既有回执。
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '15s';
ALTER TABLE exchange_account_credentials ADD CONSTRAINT uq_credential_account_identity UNIQUE(exchange_account_id,credential_id);
CREATE INDEX idx_credential_account_history ON exchange_account_credentials(exchange_account_id,credential_id DESC);

CREATE TABLE credential_operation_receipts (
    owner_user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    operation_id UUID NOT NULL,
    operation_type VARCHAR(16) NOT NULL CHECK(operation_type IN ('CREATE','ROTATE','VERIFY','DISABLE','ENABLE','EXPIRE','REVOKE')),
    input_hmac VARCHAR(64) NOT NULL CHECK(input_hmac ~ '^[a-f0-9]{64}$'),
    fingerprint_key_id VARCHAR(64) NOT NULL CHECK(fingerprint_key_id ~ '^[a-f0-9]{64}$'),
    key_version INTEGER NOT NULL CHECK(key_version>0),
    credential_id BIGINT NOT NULL,
    result_snapshot JSONB NOT NULL CHECK(jsonb_typeof(result_snapshot)='object' AND octet_length(result_snapshot::text)<=8192),
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(owner_user_id,account_id,operation_id),
    CONSTRAINT fk_credential_operation_owner FOREIGN KEY(owner_user_id,account_id)
        REFERENCES exchange_accounts(owner_user_id,exchange_account_id) ON DELETE RESTRICT,
    CONSTRAINT fk_credential_operation_version FOREIGN KEY(account_id,credential_id)
        REFERENCES exchange_account_credentials(exchange_account_id,credential_id) ON DELETE RESTRICT,
    CONSTRAINT chk_credential_operation_snapshot CHECK(
        result_snapshot ? 'credentialId' AND result_snapshot ? 'exchangeAccountId'
        AND jsonb_typeof(result_snapshot->'credentialId')='number'
        AND jsonb_typeof(result_snapshot->'exchangeAccountId')='number'
        AND (result_snapshot->>'credentialId')::BIGINT=credential_id
        AND (result_snapshot->>'exchangeAccountId')::BIGINT=account_id
        AND result_snapshot - ARRAY['credentialId','exchangeAccountId','credentialType','maskedAccessKey','credentialStatus',
            'verificationStatus','isActive','revokedAt','rotatedFromCredentialId','rotatedAt','lastVerifiedAt','lastVerificationError',
            'updatedAt','permissionProbeStatus','permissionScope','withdrawEnabled','ipAllowlistProbeStatus','failedAuthCount',
            'lastPermissionProbeAt','lastPermissionProbeError'] = '{}'::JSONB
    )
);
COMMENT ON TABLE credential_operation_receipts IS '凭据成功操作的不可变脱敏回执；不存在回执不证明操作失败或停止';
COMMENT ON COLUMN credential_operation_receipts.input_hmac IS '现有主密钥分域派生HMAC；禁止保存原输入、明文或可离线枚举的无密钥摘要';
COMMENT ON COLUMN credential_operation_receipts.fingerprint_key_id IS '指纹密钥身份；主密钥变化时拒绝重放但允许查询已有成功结果';
COMMENT ON COLUMN credential_operation_receipts.result_snapshot IS '操作提交时脱敏历史摘要；不代表当前凭据状态或交易所权限';

CREATE FUNCTION reject_credential_operation_receipt_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'credential operation receipts are immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER credential_operation_receipt_immutable BEFORE UPDATE OR DELETE ON credential_operation_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_credential_operation_receipt_change();
