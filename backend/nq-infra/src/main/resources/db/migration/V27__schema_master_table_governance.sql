-- Batch 3-A: master/config table governance.
-- Scope is intentionally limited to roles, accounts, and instrument_catalog.
-- All non-candidate domains are out of scope for this migration.

ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

COMMENT ON COLUMN roles.updated_at IS '角色配置最后更新时间；用于追踪权限主数据维护时间，不表示用户授权关系更新时间。';

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE accounts
SET status = CASE
        WHEN UPPER(status) = 'ACTIVE' THEN 'ACTIVE'
        ELSE 'DISABLED'
    END,
    updated_at = NOW()
WHERE status IS NOT NULL
  AND (status <> UPPER(status) OR UPPER(status) NOT IN ('ACTIVE', 'DISABLED'));

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_status
        CHECK (status IN ('ACTIVE', 'DISABLED'));

COMMENT ON COLUMN accounts.status IS 'legacy 账户状态，允许值：ACTIVE / DISABLED；用于兼容旧账户模型的启停语义，不表示删除。';
COMMENT ON COLUMN accounts.updated_at IS 'legacy 账户配置最后更新时间；新增时从默认值回填，后续由账户配置维护逻辑更新。';

ALTER TABLE instrument_catalog
    ADD CONSTRAINT chk_instrument_catalog_instrument_type
        CHECK (instrument_type IN ('SPOT'));

ALTER TABLE instrument_catalog
    ADD CONSTRAINT chk_instrument_catalog_status_normalized
        CHECK (status = UPPER(status) AND BTRIM(status) <> '');

COMMENT ON COLUMN instrument_catalog.instrument_type IS '产品类型，当前允许值：SPOT；后续多市场扩展必须先单独扩展枚举和验证范围。';
COMMENT ON COLUMN instrument_catalog.status IS '交易所原生 instrument 状态代码；必须为非空大写值，当前不抽象为账户 ACTIVE / DISABLED 状态。';
