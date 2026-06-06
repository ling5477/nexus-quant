-- Batch 3-B: research/backtest configuration table governance.
-- Scope is intentionally limited to research_configs and backtest_configs.
-- Lifecycle metadata here belongs to user-editable configuration records only.

ALTER TABLE research_configs
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by VARCHAR(128),
    ADD COLUMN archive_reason TEXT,
    ADD CONSTRAINT chk_research_configs_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DISABLED')),
    ADD CONSTRAINT chk_research_configs_archive_metadata
        CHECK (
            (status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status <> 'ARCHIVED'
                AND archived_at IS NULL
                AND archived_by IS NULL
                AND archive_reason IS NULL)
        );

COMMENT ON COLUMN research_configs.status IS '研究配置状态，允许值：ACTIVE / ARCHIVED / DISABLED；ARCHIVED 表示用户不再使用但仍保留可复盘配置血缘，DISABLED 表示临时停用，不表示物理删除。';
COMMENT ON COLUMN research_configs.updated_at IS '研究配置元数据最后更新时间；仅表示配置名称、参数、数据集规格或归档状态等配置元数据变化，不表示回测运行、评估结果或交易事实更新时间。';
COMMENT ON COLUMN research_configs.archived_at IS '研究配置归档时间；仅当 status=ARCHIVED 时非空，用于记录配置进入归档状态的时间。';
COMMENT ON COLUMN research_configs.archived_by IS '研究配置归档操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词或账户访问材料。';
COMMENT ON COLUMN research_configs.archive_reason IS '研究配置归档原因，可为空；用于说明归档背景，不得保存密钥、token、API secret、私钥、助记词、cookie 或账户访问材料。';

ALTER TABLE backtest_configs
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD COLUMN archived_by VARCHAR(128),
    ADD COLUMN archive_reason TEXT,
    ADD CONSTRAINT chk_backtest_configs_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DISABLED')),
    ADD CONSTRAINT chk_backtest_configs_archive_metadata
        CHECK (
            (status = 'ARCHIVED' AND archived_at IS NOT NULL)
            OR (status <> 'ARCHIVED'
                AND archived_at IS NULL
                AND archived_by IS NULL
                AND archive_reason IS NULL)
        );

COMMENT ON COLUMN backtest_configs.status IS '回测配置状态，允许值：ACTIVE / ARCHIVED / DISABLED；ARCHIVED 表示用户不再使用但仍保留回测输入血缘，DISABLED 表示临时停用，不表示物理删除。';
COMMENT ON COLUMN backtest_configs.updated_at IS '回测配置元数据最后更新时间；仅表示配置窗口、执行参数、dataset/strategy 绑定或归档状态变化，不表示运行事实、评估结果、发布记录或交易事实更新时间。';
COMMENT ON COLUMN backtest_configs.archived_at IS '回测配置归档时间；仅当 status=ARCHIVED 时非空，用于记录配置进入归档状态的时间。';
COMMENT ON COLUMN backtest_configs.archived_by IS '回测配置归档操作者标识，可为空；只保存内部用户或系统主体标识，不保存密钥、token、API secret、私钥、助记词或账户访问材料。';
COMMENT ON COLUMN backtest_configs.archive_reason IS '回测配置归档原因，可为空；用于说明归档背景，不得保存密钥、token、API secret、私钥、助记词、cookie 或账户访问材料。';
