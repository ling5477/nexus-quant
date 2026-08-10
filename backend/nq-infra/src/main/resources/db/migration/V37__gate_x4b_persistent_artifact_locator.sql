-- GateX-4B: persist server-owned opaque artifact locators on the canonical publish/release row.
-- Historical rows remain NULL/NULL; no locator is inferred from IDs, digests, paths, or local layout.

SET lock_timeout = '5s';

ALTER TABLE backtest_publish_records
    ADD COLUMN artifact_storage_key VARCHAR(128),
    ADD COLUMN manifest_storage_key VARCHAR(128);

ALTER TABLE backtest_publish_records
    ADD CONSTRAINT chk_backtest_publish_artifact_keys_pair
        CHECK (
            (artifact_storage_key IS NULL AND manifest_storage_key IS NULL)
                OR
            (artifact_storage_key IS NOT NULL AND manifest_storage_key IS NOT NULL)
            ) NOT VALID,
    ADD CONSTRAINT chk_backtest_publish_artifact_storage_key
        CHECK (
            artifact_storage_key IS NULL
                OR (
                artifact_storage_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
                    AND POSITION('..' IN artifact_storage_key) = 0
                )
            ) NOT VALID,
    ADD CONSTRAINT chk_backtest_publish_manifest_storage_key
        CHECK (
            manifest_storage_key IS NULL
                OR (
                manifest_storage_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
                    AND POSITION('..' IN manifest_storage_key) = 0
                )
            ) NOT VALID;

ALTER TABLE backtest_publish_records
    VALIDATE CONSTRAINT chk_backtest_publish_artifact_keys_pair;
ALTER TABLE backtest_publish_records
    VALIDATE CONSTRAINT chk_backtest_publish_artifact_storage_key;
ALTER TABLE backtest_publish_records
    VALIDATE CONSTRAINT chk_backtest_publish_manifest_storage_key;

CREATE FUNCTION prevent_backtest_publish_artifact_locator_rebind()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.artifact_storage_key IS DISTINCT FROM NEW.artifact_storage_key
        OR OLD.manifest_storage_key IS DISTINCT FROM NEW.manifest_storage_key THEN
        IF OLD.artifact_storage_key IS NOT NULL
            OR OLD.manifest_storage_key IS NOT NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release artifact locator is immutable';
        END IF;
        IF OLD.publish_status <> 'FAILED'
            OR NEW.publish_status <> 'SUCCEEDED'
            OR NEW.artifact_storage_key IS NULL
            OR NEW.manifest_storage_key IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release artifact locator binding is not allowed';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_backtest_publish_artifact_locator_immutable
    BEFORE UPDATE OF artifact_storage_key, manifest_storage_key
    ON backtest_publish_records
    FOR EACH ROW
EXECUTE FUNCTION prevent_backtest_publish_artifact_locator_rebind();

COMMENT ON COLUMN backtest_publish_records.artifact_storage_key IS
    '服务端生成的 Strategy Release artifact-set opaque storage key；为空表示 LEGACY_ARTIFACT_UNBOUND，禁止保存路径、URL、digest、trusted root 或客户端输入';
COMMENT ON COLUMN backtest_publish_records.manifest_storage_key IS
    '服务端生成的 Strategy Release manifest opaque storage key；与 artifact_storage_key 成对绑定，为空表示 LEGACY_ARTIFACT_UNBOUND，不冻结 filename/layout';
COMMENT ON CONSTRAINT chk_backtest_publish_artifact_keys_pair ON backtest_publish_records IS
    'artifact 与 manifest storage key 必须同时为空或同时存在；同时为空表示 LEGACY_ARTIFACT_UNBOUND';
COMMENT ON CONSTRAINT chk_backtest_publish_artifact_storage_key ON backtest_publish_records IS
    'artifact_storage_key 只允许最多 128 字符的单段 ASCII opaque identifier，禁止 path、URL、冒号和双点序列';
COMMENT ON CONSTRAINT chk_backtest_publish_manifest_storage_key ON backtest_publish_records IS
    'manifest_storage_key 只允许最多 128 字符的单段 ASCII opaque identifier，禁止 path、URL、冒号和双点序列';
COMMENT ON FUNCTION prevent_backtest_publish_artifact_locator_rebind() IS
    '禁止 Strategy Release artifact locator 静默重绑；只允许 FAILED 且未绑定的 row 在成功重试时完成首次成对绑定';
COMMENT ON TRIGGER trg_backtest_publish_artifact_locator_immutable ON backtest_publish_records IS
    '数据库层保护已绑定的 artifact/manifest storage key 不可清空或重绑';

RESET lock_timeout;
