ALTER TABLE account_snapshots
    ADD COLUMN trade_env VARCHAR(8),
    ADD COLUMN balance_basis VARCHAR(32),
    ADD COLUMN balance_scope VARCHAR(32),
    ADD COLUMN recorded_at TIMESTAMPTZ;

ALTER TABLE account_snapshots
    ADD CONSTRAINT chk_account_snapshots_trade_env
        CHECK (trade_env IS NULL OR trade_env IN ('SIM', 'LIVE')),
    ADD CONSTRAINT chk_account_snapshots_balance_basis
        CHECK (balance_basis IS NULL OR balance_basis IN ('POSITION_PROJECTION', 'LEDGER_CASH_PROJECTION')),
    ADD CONSTRAINT chk_account_snapshots_balance_scope
        CHECK (balance_scope IS NULL OR balance_scope = 'NQ_MANAGED_ACCOUNT'),
    ADD CONSTRAINT chk_account_snapshots_provenance_complete
        CHECK ((trade_env IS NULL AND balance_basis IS NULL AND balance_scope IS NULL AND recorded_at IS NULL)
            OR (trade_env IS NOT NULL AND balance_basis IS NOT NULL
                AND balance_scope IS NOT NULL AND recorded_at IS NOT NULL));

CREATE INDEX idx_account_snapshots_account_env_currency_latest
    ON account_snapshots (account_id, trade_env, currency, snapshot_id DESC);

COMMENT ON COLUMN account_snapshots.trade_env IS '写入时由 canonical 成交或 SIM 注资上下文显式提供的交易环境；历史空值表示不可判定。';
COMMENT ON COLUMN account_snapshots.balance_basis IS '仓位投影或 NQ 账本现金投影；均非交易所全账户余额。';
COMMENT ON COLUMN account_snapshots.balance_scope IS 'NQ 自身管理的账户投影范围；不代表交易所全账户资产覆盖。';
COMMENT ON COLUMN account_snapshots.ts IS '源成交或注资事件时间，不代表投影生成或对外发布时间。';
COMMENT ON COLUMN account_snapshots.recorded_at IS 'NQ 在数据库中插入投影的实际时间；事务提交可能更晚。';
