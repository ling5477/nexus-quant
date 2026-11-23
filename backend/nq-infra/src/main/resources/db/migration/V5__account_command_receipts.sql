-- 成功账户元数据命令与回执原子提交；无凭据、订单、成交或账务字段。
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '15s';
ALTER TABLE exchange_accounts ADD CONSTRAINT uq_exchange_accounts_owner_identity UNIQUE (owner_user_id, exchange_account_id);

CREATE TABLE account_command_receipts (
    owner_user_id BIGINT NOT NULL,
    command_id UUID NOT NULL,
    command_type VARCHAR(32) NOT NULL CHECK (command_type IN ('CREATE','UPDATE','ENABLE','DISABLE','SET_DEFAULT','RESOLVE_SIM')),
    request_sha256 VARCHAR(64) NOT NULL CHECK (request_sha256 ~ '^[a-f0-9]{64}$'),
    account_id BIGINT NOT NULL,
    result_snapshot JSONB NOT NULL CHECK (jsonb_typeof(result_snapshot) = 'object' AND octet_length(result_snapshot::text) <= 4096),
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (owner_user_id, command_id),
    CONSTRAINT fk_account_command_owner_account FOREIGN KEY (owner_user_id, account_id)
        REFERENCES exchange_accounts(owner_user_id, exchange_account_id) ON DELETE RESTRICT,
    CONSTRAINT chk_account_command_snapshot_identity CHECK (
        jsonb_typeof(result_snapshot->'exchangeAccountId') = 'number'
        AND jsonb_typeof(result_snapshot->'ownerUserId') = 'number'
        AND (result_snapshot->>'exchangeAccountId')::BIGINT = account_id
        AND (result_snapshot->>'ownerUserId')::BIGINT = owner_user_id
        AND result_snapshot ? 'exchangeAccountId' AND result_snapshot ? 'ownerUserId'
    )
);
COMMENT ON TABLE account_command_receipts IS '账户元数据命令成功回执；不存在回执不等于原请求失败，恢复沿用同一命令身份';
COMMENT ON COLUMN account_command_receipts.command_id IS '客户端生成并在未知结果恢复中保留的稳定UUID';
COMMENT ON COLUMN account_command_receipts.request_sha256 IS '有序长度前缀编码的非凭据元数据输入指纹；不同输入不能复用已成功身份';
COMMENT ON COLUMN account_command_receipts.result_snapshot IS '命令完成时自身账户摘要；不作为当前账户状态，不包含凭据';

CREATE FUNCTION reject_account_command_receipt_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'account command receipts are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER account_command_receipt_immutable BEFORE UPDATE OR DELETE ON account_command_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_account_command_receipt_change();
