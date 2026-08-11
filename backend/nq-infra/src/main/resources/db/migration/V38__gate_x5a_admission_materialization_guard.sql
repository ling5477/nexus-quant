-- GateX-5A: admission/materialization consistency infrastructure.
-- Historical releases receive an unbound guard state only; no digest or manifest identity is inferred.

SET lock_timeout = '5s';

CREATE TABLE strategy_release_admission_state (
    publish_record_id VARCHAR(128) PRIMARY KEY,
    admission_revision BIGINT NOT NULL DEFAULT 0,
    guard_schema_version INTEGER NOT NULL DEFAULT 1,
    release_artifact_digest VARCHAR(64),
    manifest_fingerprint VARCHAR(64),
    manifest_schema_version VARCHAR(64),
    identity_bound_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_strategy_release_admission_state_publish
        FOREIGN KEY (publish_record_id)
        REFERENCES backtest_publish_records (publish_record_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT,
    CONSTRAINT chk_strategy_release_admission_revision
        CHECK (admission_revision >= 0),
    CONSTRAINT chk_strategy_release_guard_schema_version
        CHECK (guard_schema_version = 1),
    CONSTRAINT chk_strategy_release_identity_quartet
        CHECK (
            (release_artifact_digest IS NULL
                AND manifest_fingerprint IS NULL
                AND manifest_schema_version IS NULL
                AND identity_bound_at IS NULL)
            OR
            (release_artifact_digest IS NOT NULL
                AND manifest_fingerprint IS NOT NULL
                AND manifest_schema_version IS NOT NULL
                AND identity_bound_at IS NOT NULL)
        ),
    CONSTRAINT chk_strategy_release_artifact_digest_sha256
        CHECK (release_artifact_digest IS NULL OR release_artifact_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_manifest_fingerprint_sha256
        CHECK (manifest_fingerprint IS NULL OR manifest_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_manifest_schema_version
        CHECK (manifest_schema_version IS NULL OR manifest_schema_version = 'strategy-release-manifest.v1')
);

INSERT INTO strategy_release_admission_state (
    publish_record_id,
    admission_revision,
    guard_schema_version,
    release_artifact_digest,
    manifest_fingerprint,
    manifest_schema_version,
    identity_bound_at
)
SELECT publish_record_id, 0, 1, NULL, NULL, NULL, NULL
FROM backtest_publish_records
ORDER BY publish_record_id;

CREATE INDEX idx_backtest_runs_dataset_snapshot_id
    ON backtest_runs ((dataset_snapshot_json ->> 'datasetId'));

CREATE FUNCTION initialize_strategy_release_admission_state()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO strategy_release_admission_state (publish_record_id)
    VALUES (NEW.publish_record_id)
    ON CONFLICT (publish_record_id) DO NOTHING;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_backtest_publish_admission_state_initialize
    AFTER INSERT ON backtest_publish_records
    FOR EACH ROW
EXECUTE FUNCTION initialize_strategy_release_admission_state();

CREATE FUNCTION bump_strategy_release_admission_revision(p_publish_record_id VARCHAR)
    RETURNS BIGINT
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_revision BIGINT;
BEGIN
    IF p_publish_record_id IS NULL OR btrim(p_publish_record_id) = '' THEN
        RAISE EXCEPTION USING
            ERRCODE = '23502',
            MESSAGE = 'publish_record_id is required for admission revision bump';
    END IF;

    -- Raw SQL writers reach this function after locking a source row. NOWAIT prevents a reverse
    -- source-row -> admission-state wait from deadlocking with the canonical state-first writer.
    -- The canonical coordinator already owns this row lock in the same transaction and proceeds.
    PERFORM 1
    FROM strategy_release_admission_state
    WHERE publish_record_id = p_publish_record_id
    FOR UPDATE NOWAIT;
    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23503',
            MESSAGE = 'strategy release admission state is missing';
    END IF;

    UPDATE strategy_release_admission_state
    SET admission_revision = admission_revision + 1,
        updated_at = clock_timestamp()
    WHERE publish_record_id = p_publish_record_id
    RETURNING admission_revision INTO v_revision;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING
            ERRCODE = '23503',
            MESSAGE = 'strategy release admission state is missing';
    END IF;
    RETURN v_revision;
END;
$$;

CREATE FUNCTION bump_strategy_release_admission_revisions(p_publish_record_ids VARCHAR[])
    RETURNS VOID
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_publish_record_ids VARCHAR[];
    v_publish_record_id VARCHAR;
    v_max_fan_out INTEGER;
BEGIN
    v_max_fan_out := COALESCE(
        NULLIF(current_setting('nexusquant.admission.max_fan_out', true), '')::INTEGER,
        256
    );
    IF v_max_fan_out < 1 OR v_max_fan_out > 256 THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'admission revision fan-out limit must be between 1 and 256';
    END IF;

    SELECT COALESCE(array_agg(value ORDER BY value), ARRAY[]::VARCHAR[])
    INTO v_publish_record_ids
    FROM (
        SELECT DISTINCT btrim(value) AS value
        FROM unnest(COALESCE(p_publish_record_ids, ARRAY[]::VARCHAR[])) AS ids(value)
        WHERE value IS NOT NULL AND btrim(value) <> ''
        ORDER BY value
        LIMIT v_max_fan_out + 1
    ) normalized;

    IF cardinality(v_publish_record_ids) > v_max_fan_out THEN
        RAISE EXCEPTION USING
            ERRCODE = '54000',
            MESSAGE = 'admission revision fan-out limit exceeded';
    END IF;

    FOREACH v_publish_record_id IN ARRAY v_publish_record_ids LOOP
        PERFORM bump_strategy_release_admission_revision(v_publish_record_id);
    END LOOP;
END;
$$;

CREATE FUNCTION prevent_strategy_release_identity_rebind()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.release_artifact_digest IS DISTINCT FROM NEW.release_artifact_digest
        OR OLD.manifest_fingerprint IS DISTINCT FROM NEW.manifest_fingerprint
        OR OLD.manifest_schema_version IS DISTINCT FROM NEW.manifest_schema_version
        OR OLD.identity_bound_at IS DISTINCT FROM NEW.identity_bound_at THEN
        IF OLD.release_artifact_digest IS NOT NULL
            OR OLD.manifest_fingerprint IS NOT NULL
            OR OLD.manifest_schema_version IS NOT NULL
            OR OLD.identity_bound_at IS NOT NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release identity is immutable';
        END IF;
        IF NEW.release_artifact_digest IS NULL
            OR NEW.manifest_fingerprint IS NULL
            OR NEW.manifest_schema_version IS NULL
            OR NEW.identity_bound_at IS NULL THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'strategy release identity must be bound atomically';
        END IF;
        NEW.admission_revision := OLD.admission_revision + 1;
        NEW.updated_at := clock_timestamp();
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_strategy_release_identity_immutable
    BEFORE UPDATE OF release_artifact_digest, manifest_fingerprint, manifest_schema_version, identity_bound_at
    ON strategy_release_admission_state
    FOR EACH ROW
EXECUTE FUNCTION prevent_strategy_release_identity_rebind();

CREATE FUNCTION prevent_strategy_release_revision_rewrite()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    -- 合法 revision 写入只有统一 bump function 与 identity first-bind，单次 row update 必须严格 old + 1。
    -- 直接回退会让旧 guard revision 再次命中；同值或跳跃 rewrite 也不属于 authoritative protocol。
    IF NEW.admission_revision IS DISTINCT FROM OLD.admission_revision + 1 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'strategy release admission revision must advance exactly once per row update';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_strategy_release_revision_monotonic
    BEFORE UPDATE OF admission_revision
    ON strategy_release_admission_state
    FOR EACH ROW
EXECUTE FUNCTION prevent_strategy_release_revision_rewrite();

CREATE FUNCTION bump_admission_for_publish_update()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    PERFORM bump_strategy_release_admission_revision(NEW.publish_record_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_backtest_publish_admission_revision_update
    AFTER UPDATE ON backtest_publish_records
    FOR EACH ROW
EXECUTE FUNCTION bump_admission_for_publish_update();

CREATE FUNCTION bump_admission_for_evaluation_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(p.publish_record_id ORDER BY p.publish_record_id)
    INTO v_ids
    FROM backtest_publish_records p
    WHERE p.backtest_run_id IN (
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.backtest_run_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.backtest_run_id ELSE NULL END
    );
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_backtest_eval_admission_revision
    AFTER INSERT OR UPDATE OR DELETE ON backtest_eval_reports
    FOR EACH ROW
EXECUTE FUNCTION bump_admission_for_evaluation_mutation();

CREATE FUNCTION bump_admission_for_backtest_run_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(p.publish_record_id ORDER BY p.publish_record_id)
    INTO v_ids
    FROM backtest_publish_records p
    WHERE p.backtest_run_id IN (
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.backtest_run_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.backtest_run_id ELSE NULL END
    );
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_backtest_run_admission_revision
    AFTER UPDATE OR DELETE ON backtest_runs
    FOR EACH ROW
EXECUTE FUNCTION bump_admission_for_backtest_run_mutation();

CREATE FUNCTION bump_admission_for_paper_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    PERFORM bump_strategy_release_admission_revisions(ARRAY[
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.publish_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.publish_id ELSE NULL END
    ]);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_paper_run_admission_revision
    AFTER INSERT OR UPDATE OR DELETE ON paper_trading_runs
    FOR EACH ROW
EXECUTE FUNCTION bump_admission_for_paper_mutation();

CREATE FUNCTION bump_admission_for_shadow_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    PERFORM bump_strategy_release_admission_revisions(ARRAY[
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.publish_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.publish_id ELSE NULL END
    ]);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_shadow_run_admission_revision
    AFTER INSERT OR UPDATE OR DELETE ON shadow_runs
    FOR EACH ROW
EXECUTE FUNCTION bump_admission_for_shadow_mutation();

CREATE FUNCTION bump_admission_for_consistency_mutation()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(DISTINCT s.publish_id ORDER BY s.publish_id)
    INTO v_ids
    FROM shadow_runs s
    WHERE s.id IN (
        CASE WHEN TG_OP <> 'INSERT' THEN OLD.shadow_run_id ELSE NULL END,
        CASE WHEN TG_OP <> 'DELETE' THEN NEW.shadow_run_id ELSE NULL END
    )
      AND s.publish_id IS NOT NULL;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_shadow_consistency_admission_revision
    AFTER INSERT OR UPDATE OR DELETE ON shadow_consistency_reports
    FOR EACH ROW
EXECUTE FUNCTION bump_admission_for_consistency_mutation();

CREATE FUNCTION bump_admission_for_strategy_version_update()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        WHERE p.strategy_version_id IN (
            SELECT n.strategy_version_id
            FROM strategy_version_new_rows n
            JOIN strategy_version_old_rows o USING (strategy_version_id)
            WHERE n.status IS DISTINCT FROM o.status
        )
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_strategy_version_admission_revision_update
    AFTER UPDATE ON strategy_versions
    REFERENCING OLD TABLE AS strategy_version_old_rows NEW TABLE AS strategy_version_new_rows
    FOR EACH STATEMENT
EXECUTE FUNCTION bump_admission_for_strategy_version_update();

CREATE FUNCTION bump_admission_for_strategy_version_delete()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        JOIN strategy_version_deleted_rows d ON d.strategy_version_id = p.strategy_version_id
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_strategy_version_admission_revision_delete
    AFTER DELETE ON strategy_versions
    REFERENCING OLD TABLE AS strategy_version_deleted_rows
    FOR EACH STATEMENT
EXECUTE FUNCTION bump_admission_for_strategy_version_delete();

CREATE FUNCTION bump_admission_for_dataset_update()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
        WHERE (r.dataset_snapshot_json ->> 'datasetId') IN (
            SELECT n.dataset_id::TEXT
            FROM marketdata_dataset_new_rows n
            JOIN marketdata_dataset_old_rows o USING (dataset_id)
            WHERE n.status IS DISTINCT FROM o.status
               OR n.quality_status IS DISTINCT FROM o.quality_status
               OR n.start_time IS DISTINCT FROM o.start_time
               OR n.end_time IS DISTINCT FROM o.end_time
               OR n.bar_count IS DISTINCT FROM o.bar_count
               OR n.gap_count IS DISTINCT FROM o.gap_count
        )
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_marketdata_dataset_admission_revision_update
    AFTER UPDATE ON marketdata_datasets
    REFERENCING OLD TABLE AS marketdata_dataset_old_rows NEW TABLE AS marketdata_dataset_new_rows
    FOR EACH STATEMENT
EXECUTE FUNCTION bump_admission_for_dataset_update();

CREATE FUNCTION bump_admission_for_dataset_delete()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_ids VARCHAR[];
BEGIN
    SELECT array_agg(bounded.publish_record_id ORDER BY bounded.publish_record_id)
    INTO v_ids
    FROM (
        SELECT DISTINCT p.publish_record_id
        FROM backtest_publish_records p
        JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
        JOIN marketdata_dataset_deleted_rows d
          ON d.dataset_id::TEXT = r.dataset_snapshot_json ->> 'datasetId'
        ORDER BY p.publish_record_id
        LIMIT 257
    ) bounded;
    PERFORM bump_strategy_release_admission_revisions(v_ids);
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_marketdata_dataset_admission_revision_delete
    AFTER DELETE ON marketdata_datasets
    REFERENCING OLD TABLE AS marketdata_dataset_deleted_rows
    FOR EACH STATEMENT
EXECUTE FUNCTION bump_admission_for_dataset_delete();

COMMENT ON TABLE strategy_release_admission_state IS
    'GateX-5A Strategy Release admission 一致性状态；历史 row 仅初始化 revision=0 且 identity quartet 全 NULL，不推测或回填 digest/fingerprint';
COMMENT ON COLUMN strategy_release_admission_state.publish_record_id IS
    '唯一 release identity，对应 backtest_publish_records.publish_record_id；同时作为主键和 RESTRICT 外键';
COMMENT ON COLUMN strategy_release_admission_state.admission_revision IS
    'admission-sensitive source fact 的单调版本；业务只依赖发生变化，不依赖每次严格加一';
COMMENT ON COLUMN strategy_release_admission_state.guard_schema_version IS
    'guard persistence schema 版本，GateX-5A 固定为 1';
COMMENT ON COLUMN strategy_release_admission_state.release_artifact_digest IS
    '经服务端验证后的 release artifact-set SHA-256；NULL 表示 LEGACY_RELEASE_IDENTITY_UNBOUND，禁止历史推测';
COMMENT ON COLUMN strategy_release_admission_state.manifest_fingerprint IS
    'strategy-release-manifest-fingerprint.v1 canonical encoding 的 SHA-256；不哈希 raw JSON';
COMMENT ON COLUMN strategy_release_admission_state.manifest_schema_version IS
    '已验证 manifest schema，当前只允许 strategy-release-manifest.v1';
COMMENT ON COLUMN strategy_release_admission_state.identity_bound_at IS
    'identity quartet 首次原子绑定时间；绑定后不可清空或修改';
COMMENT ON COLUMN strategy_release_admission_state.created_at IS 'admission state 初始化时间';
COMMENT ON COLUMN strategy_release_admission_state.updated_at IS '最近 revision bump 或 identity first-bind 时间';
COMMENT ON FUNCTION bump_strategy_release_admission_revision(VARCHAR) IS
    '统一单 release revision bump；state row 缺失时 fail-closed，禁止 silent no-op';
COMMENT ON FUNCTION bump_strategy_release_admission_revisions(VARCHAR[]) IS
    '去重并按 publish_record_id 升序 bump；fan-out 硬上限 256，可通过事务配置收紧但不可放宽，超限 fail-closed';
COMMENT ON FUNCTION prevent_strategy_release_identity_rebind() IS
    '数据库层仅允许 identity quartet 从全 NULL 到完整 non-NULL 一次，之后禁止 rebind/clear/partial mutation';
COMMENT ON FUNCTION prevent_strategy_release_revision_rewrite() IS
    '禁止 direct SQL 回退、同值或跳跃改写 admission revision；每次 row update 只允许 authoritative old + 1';
COMMENT ON INDEX idx_backtest_runs_dataset_snapshot_id IS
    '支持 datasetId -> backtest run snapshot -> publish 的 admission revision reverse mapping';

RESET lock_timeout;
