-- DRAFT ONLY
-- PRE-GATEX PREPARATION / UNMERGED
-- NOT A FLYWAY MIGRATION
-- MUST NOT BE EXECUTED AGAINST DEV OR PRODUCTION DATABASES
--
-- Task: NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION-ATTEMPT-02
-- Purpose: review-only candidate schema; every DDL statement is deliberately enclosed in a block comment.
-- No statement in this file has been executed against any database.
--
-- Decision map:
--   [REUSE]  strategy_versions
--   [EXTEND] backtest_publish_records as the single publish/release anchor
--   [NEW]    strategy_release_lifecycle_events (append-only proposal)
--   [NEW]    strategy_release_artifact_files (manifest file-index proposal)
--   [NEW]    strategy_release_artifact_verifications (append-only integrity facts proposal)
--   [REUSE]  shadow_runs, shadow_run_events, shadow_run_snapshots, shadow_consistency_reports
--   [DEFER]  risk_limit_sets; GateX candidate uses immutable manifest.riskBudget snapshot
--
-- Integrity/verification does not mean strategy approval, Shadow start, LIVE readiness,
-- order authorization, credential access, or private-endpoint permission.

/*
-- ============================================================================
-- [REUSE] strategy_versions
-- ============================================================================
-- Reuse strategy_versions.strategy_version_id and immutable snapshots/checksum.
-- The ID is an opaque application ID (currently sv-<UUID>), not a PostgreSQL UUID.
-- No candidate DDL is required for strategy_versions.

-- ============================================================================
-- [EXTEND] backtest_publish_records
-- ============================================================================
-- Why: existing publish records already bind backtest/evaluation/strategy-version facts.
-- Extending that anchor avoids a second publish source of truth. Existing rows remain NULL
-- and must not be backfilled as VERIFIED/PUBLISHED without evidence.

ALTER TABLE backtest_publish_records
    ADD COLUMN release_manifest_schema_version VARCHAR(64),
    ADD COLUMN release_manifest_json JSONB,
    ADD COLUMN release_manifest_digest CHAR(64),
    ADD COLUMN release_lifecycle_status VARCHAR(32),
    ADD COLUMN release_lifecycle_version BIGINT,
    ADD COLUMN release_verified_at TIMESTAMPTZ,
    ADD COLUMN release_retired_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_backtest_publish_records_release_manifest_object
        CHECK (release_manifest_json IS NULL OR jsonb_typeof(release_manifest_json) = 'object'),
    ADD CONSTRAINT chk_backtest_publish_records_release_manifest_digest
        CHECK (release_manifest_digest IS NULL OR release_manifest_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_backtest_publish_records_release_manifest_bundle
        CHECK (
            (release_manifest_schema_version IS NULL AND release_manifest_json IS NULL AND release_manifest_digest IS NULL)
            OR
            (release_manifest_schema_version IS NOT NULL AND release_manifest_json IS NOT NULL AND release_manifest_digest IS NOT NULL)
        ),
    ADD CONSTRAINT chk_backtest_publish_records_release_lifecycle_status
        CHECK (release_lifecycle_status IS NULL OR release_lifecycle_status IN
               ('DRAFT', 'CANDIDATE', 'VERIFIED', 'PUBLISHED', 'REJECTED', 'RETIRED')),
    ADD CONSTRAINT chk_backtest_publish_records_release_lifecycle_version
        CHECK (release_lifecycle_version IS NULL OR release_lifecycle_version >= 0);

COMMENT ON COLUMN backtest_publish_records.release_manifest_schema_version IS
    'GateX 候选：strategy release manifest schema version；为空表示历史 publish 未纳入 release lifecycle';
COMMENT ON COLUMN backtest_publish_records.release_manifest_json IS
    'GateX 候选：不可变 release manifest 快照；禁止 credential、token、cookie、private request/response';
COMMENT ON COLUMN backtest_publish_records.release_manifest_digest IS
    'GateX 候选：artifactFiles canonical index 的 SHA-256；完整性不表示策略批准或交易授权';
COMMENT ON COLUMN backtest_publish_records.release_lifecycle_status IS
    'GateX 候选状态：DRAFT、CANDIDATE、VERIFIED、PUBLISHED、REJECTED、RETIRED；为空表示未纳入';
COMMENT ON COLUMN backtest_publish_records.release_lifecycle_version IS
    'GateX 候选乐观锁版本；状态更新必须校验 expected version，防止并发覆盖';
COMMENT ON COLUMN backtest_publish_records.release_verified_at IS
    'GateX 候选 manifest/file 完整性验证时间；不表示 Shadow 已启动或 LIVE 已授权';
COMMENT ON COLUMN backtest_publish_records.release_retired_at IS
    'GateX 候选 release 退役时间；退役为终态，不得恢复到运行或发布态';

-- ============================================================================
-- [NEW] strategy_release_lifecycle_events
-- ============================================================================
-- Append-only result cache and audit stream. A unique action_id is bound to the first
-- request/result for one publish anchor; conflicting reuse must fail closed.

CREATE TABLE strategy_release_lifecycle_events (
    id UUID PRIMARY KEY,
    publish_record_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    requested_status VARCHAR(32) NOT NULL,
    resulting_status VARCHAR(32) NOT NULL,
    accepted BOOLEAN NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    trigger_action VARCHAR(64) NOT NULL,
    manifest_digest CHAR(64),
    actor_id BIGINT,
    trace_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_strategy_release_lifecycle_events_publish
        FOREIGN KEY (publish_record_id) REFERENCES backtest_publish_records (publish_record_id),
    CONSTRAINT uq_strategy_release_lifecycle_events_action
        UNIQUE (publish_record_id, action_id),
    CONSTRAINT chk_strategy_release_lifecycle_events_statuses
        CHECK (from_status IN ('DRAFT', 'CANDIDATE', 'VERIFIED', 'PUBLISHED', 'REJECTED', 'RETIRED')
           AND requested_status IN ('DRAFT', 'CANDIDATE', 'VERIFIED', 'PUBLISHED', 'REJECTED', 'RETIRED')
           AND resulting_status IN ('DRAFT', 'CANDIDATE', 'VERIFIED', 'PUBLISHED', 'REJECTED', 'RETIRED')),
    CONSTRAINT chk_strategy_release_lifecycle_events_digest
        CHECK (manifest_digest IS NULL OR manifest_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_strategy_release_lifecycle_events_publish_created
    ON strategy_release_lifecycle_events (publish_record_id, created_at DESC);

COMMENT ON TABLE strategy_release_lifecycle_events IS
    'GateX 候选 append-only release lifecycle 审计事件；不保存凭证，不产生 Shadow/LIVE/交易授权';
COMMENT ON COLUMN strategy_release_lifecycle_events.id IS '候选事件 UUID 主键';
COMMENT ON COLUMN strategy_release_lifecycle_events.publish_record_id IS '既有 publish anchor，避免平行 publish 主链';
COMMENT ON COLUMN strategy_release_lifecycle_events.action_id IS '幂等 action ID；同一 publish 下唯一并绑定首次请求结果';
COMMENT ON COLUMN strategy_release_lifecycle_events.from_status IS '动作前 release 状态';
COMMENT ON COLUMN strategy_release_lifecycle_events.requested_status IS '请求目标状态';
COMMENT ON COLUMN strategy_release_lifecycle_events.resulting_status IS 'fail-closed 后实际状态；非法流转保持原状态';
COMMENT ON COLUMN strategy_release_lifecycle_events.accepted IS '动作是否接受；false 也必须形成审计事实';
COMMENT ON COLUMN strategy_release_lifecycle_events.reason_code IS '结构化原因码；不得含原始 payload 或敏感信息';
COMMENT ON COLUMN strategy_release_lifecycle_events.trigger_action IS '触发动作枚举，例如 VERIFY_MANIFEST';
COMMENT ON COLUMN strategy_release_lifecycle_events.manifest_digest IS '动作绑定的 manifest digest；完整性不等于授权';
COMMENT ON COLUMN strategy_release_lifecycle_events.actor_id IS '本地操作者 ID；为空表示系统测试主体';
COMMENT ON COLUMN strategy_release_lifecycle_events.trace_id IS '脱敏 trace ID；不保存 token 或签名串';
COMMENT ON COLUMN strategy_release_lifecycle_events.created_at IS 'append-only 事件创建 UTC 时间';

-- ============================================================================
-- [NEW] strategy_release_artifact_files
-- ============================================================================

CREATE TABLE strategy_release_artifact_files (
    id UUID PRIMARY KEY,
    publish_record_id VARCHAR(128) NOT NULL,
    manifest_digest CHAR(64) NOT NULL,
    logical_name VARCHAR(128) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_strategy_release_artifact_files_publish
        FOREIGN KEY (publish_record_id) REFERENCES backtest_publish_records (publish_record_id),
    CONSTRAINT uq_strategy_release_artifact_files_identity
        UNIQUE (publish_record_id, logical_name, relative_path),
    CONSTRAINT chk_strategy_release_artifact_files_manifest_digest
        CHECK (manifest_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_artifact_files_sha256
        CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_artifact_files_size
        CHECK (size_bytes > 0 AND size_bytes <= 1073741824),
    CONSTRAINT chk_strategy_release_artifact_files_relative_path
        CHECK (relative_path ~ '^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$'
           AND relative_path !~ '(^|/)\.\.(/|$)')
);

COMMENT ON TABLE strategy_release_artifact_files IS
    'GateX 候选 manifest file index；只保存相对路径和完整性 metadata，不保存文件内容或凭证';
COMMENT ON COLUMN strategy_release_artifact_files.id IS '候选 artifact file UUID 主键';
COMMENT ON COLUMN strategy_release_artifact_files.publish_record_id IS '既有 publish anchor';
COMMENT ON COLUMN strategy_release_artifact_files.manifest_digest IS '所属 manifest canonical digest';
COMMENT ON COLUMN strategy_release_artifact_files.logical_name IS '稳定逻辑名，用于排序和人工审计';
COMMENT ON COLUMN strategy_release_artifact_files.relative_path IS '仅允许 / 分隔的相对路径；禁止绝对路径、盘符、UNC、反斜杠和 ..';
COMMENT ON COLUMN strategy_release_artifact_files.sha256 IS '文件内容 SHA-256，小写 64 位十六进制';
COMMENT ON COLUMN strategy_release_artifact_files.size_bytes IS 'manifest 声明的文件字节数，上限 1 GiB；正式 GateX 应采用更保守配置';
COMMENT ON COLUMN strategy_release_artifact_files.media_type IS 'allowlisted media type；不得据此自动执行文件';
COMMENT ON COLUMN strategy_release_artifact_files.created_at IS 'file-index fact 创建 UTC 时间';

-- ============================================================================
-- [NEW] strategy_release_artifact_verifications
-- ============================================================================
-- This table records actual recomputation facts. It must not be populated from a caller's
-- expected digest without reading and hashing every allowlisted file under a trusted root.

CREATE TABLE strategy_release_artifact_verifications (
    id UUID PRIMARY KEY,
    publish_record_id VARCHAR(128) NOT NULL,
    action_id VARCHAR(128) NOT NULL,
    manifest_digest CHAR(64) NOT NULL,
    computed_artifact_digest CHAR(64),
    verification_status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    verified_file_count INTEGER NOT NULL,
    verifier_version VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    verified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_strategy_release_artifact_verifications_publish
        FOREIGN KEY (publish_record_id) REFERENCES backtest_publish_records (publish_record_id),
    CONSTRAINT uq_strategy_release_artifact_verifications_action
        UNIQUE (publish_record_id, action_id),
    CONSTRAINT chk_strategy_release_artifact_verifications_manifest_digest
        CHECK (manifest_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_artifact_verifications_computed_digest
        CHECK (computed_artifact_digest IS NULL OR computed_artifact_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_artifact_verifications_status
        CHECK (verification_status IN ('VERIFIED', 'MISMATCH', 'INVALID', 'UNAVAILABLE')),
    CONSTRAINT chk_strategy_release_artifact_verifications_file_count
        CHECK (verified_file_count >= 0)
);

CREATE INDEX idx_strategy_release_artifact_verifications_publish_verified
    ON strategy_release_artifact_verifications (publish_record_id, verified_at DESC);

COMMENT ON TABLE strategy_release_artifact_verifications IS
    'GateX 候选 append-only artifact 完整性验证事实；VERIFIED 不表示策略批准、Shadow 启动、LIVE ready 或交易授权';
COMMENT ON COLUMN strategy_release_artifact_verifications.id IS '候选 verification UUID 主键';
COMMENT ON COLUMN strategy_release_artifact_verifications.publish_record_id IS '既有 publish anchor';
COMMENT ON COLUMN strategy_release_artifact_verifications.action_id IS '验证幂等 ID；同一 publish 下唯一';
COMMENT ON COLUMN strategy_release_artifact_verifications.manifest_digest IS 'manifest 声明的 canonical digest';
COMMENT ON COLUMN strategy_release_artifact_verifications.computed_artifact_digest IS '读取 allowlisted files 后重算的 digest；不可用时为空';
COMMENT ON COLUMN strategy_release_artifact_verifications.verification_status IS 'VERIFIED、MISMATCH、INVALID、UNAVAILABLE；只表达完整性';
COMMENT ON COLUMN strategy_release_artifact_verifications.reason_code IS '结构化脱敏原因码';
COMMENT ON COLUMN strategy_release_artifact_verifications.verified_file_count IS '本次实际完成 hash 的文件数';
COMMENT ON COLUMN strategy_release_artifact_verifications.verifier_version IS 'verifier 实现版本，用于复现 canonicalization';
COMMENT ON COLUMN strategy_release_artifact_verifications.trace_id IS '脱敏 trace ID';
COMMENT ON COLUMN strategy_release_artifact_verifications.verified_at IS '验证完成 UTC 时间';

-- ============================================================================
-- [REUSE] Shadow Session
-- ============================================================================
-- Reuse shadow_runs, shadow_run_events, shadow_run_snapshots and
-- shadow_consistency_reports. shadow_runs.publish_id already points to the publish anchor.
-- Do not create shadow_sessions.

-- ============================================================================
-- [DEFER] Risk Limit Set
-- ============================================================================
-- Do not create risk_limit_sets in GateX without evidence for independent version sharing,
-- query ownership, lifecycle and retention. Use immutable manifest.riskBudget snapshot first.

-- Formal GateX migration review must separately cover historical-row semantics, online DDL,
-- lock duration, batch/backfill, index build strategy, retention, authorization, rollback and
-- removal of any default that could fabricate VERIFIED/PUBLISHED history.
*/
