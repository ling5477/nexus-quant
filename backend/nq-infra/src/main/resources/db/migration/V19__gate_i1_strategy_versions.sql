-- V19__gate_i1_strategy_versions.sql
-- 目的：落实 GateI-1 策略版本与发布记录绑定的最小 schema。
-- Why:
-- 1) strategy_definitions 表代表当前策略定义，不足以承担回测、发布、Paper run 的不可变输入快照；
-- 2) backtest_publish_records 需要绑定明确策略版本，并固化 version snapshot，保证后续 GateI-2/3/4 可追溯；
-- 3) 本 migration 只新增 GateI-1 所需表/字段，不修改策略核心算法、交易状态机或回测核心算法。

CREATE TABLE strategy_versions (
    strategy_version_id VARCHAR(128) PRIMARY KEY,
    strategy_code VARCHAR(128) NOT NULL,
    version INTEGER NOT NULL,
    version_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    param_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    checksum VARCHAR(128) NOT NULL,
    created_by VARCHAR(512) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_strategy_versions_strategy_code
        FOREIGN KEY (strategy_code) REFERENCES strategy_definitions (strategy_code),
    CONSTRAINT uq_strategy_versions_strategy_code_version
        UNIQUE (strategy_code, version),
    CONSTRAINT chk_strategy_versions_version
        CHECK (version > 0),
    CONSTRAINT chk_strategy_versions_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_strategy_versions_strategy_code
    ON strategy_versions (strategy_code, version DESC);

CREATE INDEX idx_strategy_versions_status
    ON strategy_versions (status, updated_at DESC);

CREATE INDEX idx_strategy_versions_created_at
    ON strategy_versions (created_at DESC);

ALTER TABLE backtest_publish_records
    ADD COLUMN strategy_version_id VARCHAR(128),
    ADD COLUMN version_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE backtest_publish_records
    ADD CONSTRAINT fk_backtest_publish_records_strategy_version
        FOREIGN KEY (strategy_version_id) REFERENCES strategy_versions (strategy_version_id);

CREATE INDEX idx_backtest_publish_records_strategy_version
    ON backtest_publish_records (strategy_version_id);

COMMENT ON TABLE strategy_versions IS 'GateI-1 策略版本表，记录策略定义可用于回测、发布和后续 Paper Trading 的不可变版本快照';
COMMENT ON COLUMN strategy_versions.strategy_version_id IS '策略版本 ID，业务主键，格式由应用生成';
COMMENT ON COLUMN strategy_versions.strategy_code IS '关联策略编码，对应 strategy_definitions.strategy_code，表示该版本所属策略定义';
COMMENT ON COLUMN strategy_versions.version IS '策略版本号，同一 strategy_code 下单调递增，用于版本排序和幂等审计';
COMMENT ON COLUMN strategy_versions.version_name IS '策略版本展示名称，用于前端展示和人工审计，不参与策略算法执行';
COMMENT ON COLUMN strategy_versions.status IS '策略版本状态，允许值：DRAFT、ACTIVE、ARCHIVED；GateI-1 第一版只做版本管理和发布引用';
COMMENT ON COLUMN strategy_versions.param_snapshot_json IS '策略参数快照 JSON，用于回测、发布和后续 Paper run 复现输入；不得保存密钥、token、cookie';
COMMENT ON COLUMN strategy_versions.config_snapshot_json IS '策略配置快照 JSON，通常来自 strategy_definitions.config_snapshot 或创建请求覆盖；不得保存敏感凭证';
COMMENT ON COLUMN strategy_versions.source_snapshot_json IS '策略来源快照 JSON，用于记录策略定义、代码引用或外部来源摘要；不得保存敏感凭证';
COMMENT ON COLUMN strategy_versions.checksum IS '参数、配置、来源快照的 SHA-256 校验摘要，用于识别版本内容是否一致';
COMMENT ON COLUMN strategy_versions.created_by IS '创建人标识，来自 API principal 或系统默认值，用于审计';
COMMENT ON COLUMN strategy_versions.created_at IS '策略版本创建时间';
COMMENT ON COLUMN strategy_versions.updated_at IS '策略版本最后更新时间，GateI-1 创建后通常只随状态维护变化';

COMMENT ON COLUMN backtest_publish_records.strategy_version_id IS 'GateI-1 发布记录绑定的策略版本 ID，对应 strategy_versions.strategy_version_id；为空表示历史发布记录尚未绑定版本';
COMMENT ON COLUMN backtest_publish_records.version_snapshot_json IS 'GateI-1 发布时固化的策略版本快照 JSON，包含策略编码、版本号、参数、配置、来源和 checksum；不得保存密钥、token、cookie';
