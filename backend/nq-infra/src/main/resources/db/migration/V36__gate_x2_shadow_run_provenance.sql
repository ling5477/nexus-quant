-- GateX-2: persist the verified strategy artifact provenance on Shadow Run creation.
-- Historical rows remain unbound or publish-only; no digest is inferred or backfilled.

ALTER TABLE shadow_runs
    ADD COLUMN artifact_digest VARCHAR(64);

ALTER TABLE shadow_runs
    ADD CONSTRAINT chk_shadow_runs_artifact_digest_sha256
        CHECK (artifact_digest IS NULL OR artifact_digest ~ '^[0-9a-f]{64}$') NOT VALID,
    ADD CONSTRAINT chk_shadow_runs_artifact_requires_publish
        CHECK (artifact_digest IS NULL OR publish_id IS NOT NULL) NOT VALID;

-- 将历史行扫描放到较弱锁级别的 VALIDATE 阶段；迁移完成后两个约束均为已验证状态。
ALTER TABLE shadow_runs
    VALIDATE CONSTRAINT chk_shadow_runs_artifact_digest_sha256;

ALTER TABLE shadow_runs
    VALIDATE CONSTRAINT chk_shadow_runs_artifact_requires_publish;

COMMENT ON COLUMN shadow_runs.artifact_digest IS 'Shadow Run 创建时固化的 strategy release artifact SHA-256，小写 64 位十六进制；为空表示历史未绑定或仅绑定 publish_id，不做推测或回填，不表示 admission、交易批准或 LIVE ready';
COMMENT ON CONSTRAINT chk_shadow_runs_artifact_digest_sha256 ON shadow_runs IS 'artifact_digest 为空或严格为 64 位小写十六进制 SHA-256；禁止空字符串、大写和非十六进制值';
COMMENT ON CONSTRAINT chk_shadow_runs_artifact_requires_publish ON shadow_runs IS '存在 artifact_digest 时必须同时存在 publish_id；允许历史无绑定和仅 publish_id 绑定';
