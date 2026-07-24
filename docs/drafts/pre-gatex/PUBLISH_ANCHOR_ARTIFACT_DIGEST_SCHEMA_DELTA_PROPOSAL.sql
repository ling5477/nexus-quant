-- DRAFT ONLY
-- PRE-GATEX PREPARATION / UNMERGED
-- NOT A FLYWAY MIGRATION
-- DO NOT EXECUTE AGAINST DEV, SOAK OR PRODUCTION DATABASES
--
-- Task: NQ-PRE-GATEX-PUBLISH-ANCHOR-ARTIFACT-DIGEST-CONTRACT-PROTOTYPE-ATTEMPT-01
-- Decision: Strategy Release canonical identity is backtest_publish_records.publish_record_id
-- (VARCHAR(128)); shadow_runs.publish_id remains the only release anchor.
--
-- This proposal is deliberately enclosed in a block comment. It is a review artifact, not
-- executable database work and must be converted into a separately reviewed forward-only
-- GateX Flyway migration only after GateW acceptance authorizes that work.

/*
-- Existing FK is retained; do not add shadow_runs.release_id, release_publish_id, or a
-- strategy_releases UUID table.
ALTER TABLE shadow_runs
    ADD COLUMN artifact_digest VARCHAR(64);

ALTER TABLE shadow_runs
    ADD CONSTRAINT chk_shadow_runs_artifact_digest_format
        CHECK (artifact_digest IS NULL OR artifact_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_shadow_runs_artifact_digest_requires_publish
        CHECK (artifact_digest IS NULL OR publish_id IS NOT NULL);

COMMENT ON COLUMN shadow_runs.artifact_digest IS
    'GateX 候选：与 publish_id 共同表达冻结 Strategy Release artifact provenance 的 64 位小写 SHA-256；为空表示 legacy 未记录 digest，不表示验证通过、Shadow 已启动、交易授权或 LIVE ready';

-- V32 已有 fk_shadow_runs_publish：publish_id -> backtest_publish_records.publish_record_id。
-- 当前没有 publish_id 创建时间索引，因此候选增加该索引以支撑按 release anchor 的有界审计查询。
CREATE INDEX idx_shadow_runs_publish_created_at
    ON shadow_runs (publish_id, created_at DESC);

-- 禁止：UNIQUE (publish_id, artifact_digest)。一个 publish/artifact 可在不同窗口、trace 或
-- diagnostic request 下对应多个 Shadow Run；创建去重继续由唯一 idempotency_key 承担。

-- Legacy compatibility / no fake backfill:
--   publish_id IS NULL, artifact_digest IS NULL -> LEGACY_UNBOUND
--   publish_id IS NOT NULL, artifact_digest IS NULL -> LEGACY_PUBLISH_ONLY
--   publish_id IS NOT NULL, artifact_digest IS NOT NULL -> RELEASE_BOUND
-- 历史行不从 snapshot checksum、JSON payload 或其他表推导 artifact_digest，不做静默回填。

-- Forward-only repair / rollback:
-- 1. 该字段保持 nullable，失败部署时应用继续按 legacy mode 读取，不删除或伪造历史值。
-- 2. 若 GateX 创建路径发现 binding 错误，应停止新 release-bound 创建并以新的 forward-only
--    repair migration 修复；不得 DROP COLUMN、级联删除或改写历史 provenance。
-- 3. 正式执行前必须评估 shadow_runs 表规模、ALTER TABLE 锁表窗口、索引建造策略、现有行
--    校验与应用版本兼容性。
*/
