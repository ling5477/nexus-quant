-- 原调度操作的回执，不是第二套任务或交易事实源；旧控制、旧迁移和历史结果保持原样。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE scheduled_job_operations (
    operation_id uuid PRIMARY KEY,
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor varchar(128) NOT NULL,
    operation_type varchar(24) NOT NULL,
    job_key varchar(48) NOT NULL REFERENCES scheduled_job_controls(job_key) ON DELETE RESTRICT,
    input_sha256 char(64) NOT NULL,
    trace_id varchar(128) NOT NULL,
    status varchar(24) NOT NULL,
    control_json jsonb,
    execution_result varchar(16),
    reason_code varchar(64),
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    completed_at timestamptz,
    CONSTRAINT chk_scheduled_operation_identity CHECK (btrim(actor)<>'' AND btrim(trace_id)<>'' AND input_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT chk_scheduled_operation_status CHECK (status IN ('STARTING','RUNNING','COMPLETED','REJECTED','OUTCOME_UNKNOWN')),
    CONSTRAINT chk_scheduled_operation_type CHECK (operation_type IN ('UPDATE_CONTROL','RUN_ONCE')),
    CONSTRAINT chk_scheduled_operation_result CHECK ((
        (operation_type='UPDATE_CONTROL' AND status IN ('STARTING','COMPLETED') AND execution_result IS NULL
            AND ((status='STARTING' AND control_json IS NULL) OR (status='COMPLETED' AND jsonb_typeof(control_json)='object' AND octet_length(control_json::text)<=16384
                AND control_json->>'jobKey'=job_key AND jsonb_typeof(control_json->'enabled')='boolean'
                AND (control_json->>'fixedDelayMs') ~ '^[0-9]{1,8}$'
                AND (control_json->>'fixedDelayMs')::bigint BETWEEN 1000 AND 86400000
                AND (control_json->>'version') ~ '^[0-9]{1,19}$' AND (control_json->>'version')::numeric BETWEEN 0 AND 9223372036854775807
                AND control_json->>'lastStatus' IN ('NEVER_RUN','RUNNING','SUCCESS','FAILED','SKIPPED')
                AND jsonb_typeof(control_json->'updatedAt')='string')))
        OR (operation_type='RUN_ONCE' AND control_json IS NULL
            AND ((status='COMPLETED' AND execution_result IN ('SUCCESS','FAILED','SKIPPED')) OR (status<>'COMPLETED' AND execution_result IS NULL)))
    ) IS TRUE),
    CONSTRAINT chk_scheduled_operation_phase CHECK (
        (operation_type='UPDATE_CONTROL' AND started_at IS NULL)
        OR (operation_type='RUN_ONCE' AND (status<>'RUNNING' OR started_at IS NOT NULL))),
    CONSTRAINT chk_scheduled_operation_rejection CHECK ((status<>'REJECTED' OR reason_code ~ '^[A-Z][A-Z0-9_]{1,63}$') IS TRUE),
    CONSTRAINT chk_scheduled_operation_time CHECK (isfinite(created_at)
        AND (started_at IS NULL OR (isfinite(started_at) AND started_at>=created_at))
        AND ((status IN ('COMPLETED','REJECTED') AND completed_at IS NOT NULL AND isfinite(completed_at) AND completed_at>=coalesce(started_at,created_at))
            OR (status NOT IN ('COMPLETED','REJECTED') AND completed_at IS NULL)))
);

CREATE INDEX idx_scheduled_operations_owner_time ON scheduled_job_operations(owner_user_id,created_at DESC,operation_id);
CREATE UNIQUE INDEX idx_scheduled_operations_pending_run ON scheduled_job_operations(job_key)
    WHERE operation_type='RUN_ONCE' AND status IN ('STARTING','RUNNING','OUTCOME_UNKNOWN');

CREATE FUNCTION guard_scheduled_job_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'scheduler operation history is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.operation_id IS DISTINCT FROM OLD.operation_id OR NEW.owner_user_id IS DISTINCT FROM OLD.owner_user_id
        OR NEW.actor IS DISTINCT FROM OLD.actor OR NEW.operation_type IS DISTINCT FROM OLD.operation_type
        OR NEW.job_key IS DISTINCT FROM OLD.job_key OR NEW.input_sha256 IS DISTINCT FROM OLD.input_sha256
        OR NEW.trace_id IS DISTINCT FROM OLD.trace_id OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR (OLD.started_at IS NOT NULL AND NEW.started_at IS DISTINCT FROM OLD.started_at)
        OR (OLD.status IN ('COMPLETED','REJECTED') AND NEW IS DISTINCT FROM OLD)
        OR NOT ((OLD.status='STARTING' AND NEW.status IN ('RUNNING','COMPLETED','REJECTED','OUTCOME_UNKNOWN'))
            OR (OLD.status='RUNNING' AND NEW.status IN ('COMPLETED','OUTCOME_UNKNOWN'))
            OR (OLD.status='OUTCOME_UNKNOWN' AND NEW.status='COMPLETED') OR NEW IS NOT DISTINCT FROM OLD) THEN
        RAISE EXCEPTION 'scheduler operation identity or transition is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_scheduled_operation_immutable BEFORE UPDATE OR DELETE ON scheduled_job_operations
    FOR EACH ROW EXECUTE FUNCTION guard_scheduled_job_operation();

COMMENT ON TABLE scheduled_job_operations IS '运维请求的稳定身份与实际结果；控制配置和任务执行事实仍由原调度器拥有，未知结果不得重复执行';
COMMENT ON COLUMN scheduled_job_operations.operation_id IS '客户端在发送前保存的原操作UUID；重复请求必须匹配owner与全部输入摘要';
COMMENT ON COLUMN scheduled_job_operations.owner_user_id IS '发起操作的规范用户ID；回执不因ADMIN角色向另一用户暴露';
COMMENT ON COLUMN scheduled_job_operations.input_sha256 IS '任务、动作、完整控制输入和用户ID的SHA256；同编号不同输入拒绝';
COMMENT ON COLUMN scheduled_job_operations.status IS 'STARTING尚未领取，RUNNING已原子领取，COMPLETED已有实际结束事实，REJECTED前置拒绝，OUTCOME_UNKNOWN不重跑';
COMMENT ON COLUMN scheduled_job_operations.control_json IS '版本控制更新事务的原始结果快照；后来任务行变化不覆盖原成功回执';
COMMENT ON COLUMN scheduled_job_operations.execution_result IS '原调度器的SUCCESS/FAILED/SKIPPED结果；不表示没有业务副作用或具备交易权限';
COMMENT ON INDEX idx_scheduled_operations_pending_run IS '同一任务最多一条未收敛的显式运行请求，防换编号绕过未知结果';
COMMENT ON FUNCTION guard_scheduled_job_operation() IS '冻结原请求身份与终态；迟到的原执行可从未知状态完成，不允许回到可执行状态';
