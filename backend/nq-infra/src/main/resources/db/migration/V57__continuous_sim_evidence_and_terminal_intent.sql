SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';

ALTER TABLE continuous_sim_overlap_checks
    ADD COLUMN response_kind varchar(24),
    ADD COLUMN response_fingerprint char(64),
    ADD COLUMN response_prefix bytea,
    ADD COLUMN response_body_sha256 char(64),
    ADD COLUMN response_captured_at timestamptz,
    ADD COLUMN captured_result varchar(16),
    ADD COLUMN captured_failure_reason varchar(48),
    ADD COLUMN response_retryable boolean NOT NULL DEFAULT false,
    ADD COLUMN captured_revision_at timestamptz,
    ADD COLUMN revised_open_time timestamptz,
    ADD COLUMN original_bar_sha256 char(64),
    ADD COLUMN revised_bar_sha256 char(64),
    ADD CONSTRAINT ck_continuous_overlap_capture CHECK ((
        (response_captured_at IS NULL AND NOT response_retryable AND
         num_nonnulls(response_kind,response_fingerprint,response_prefix,response_body_sha256,captured_result,captured_failure_reason,
             captured_revision_at,revised_open_time,original_bar_sha256,revised_bar_sha256)=0)
        OR (response_captured_at IS NOT NULL AND isfinite(response_captured_at)
            AND response_kind IN ('COMPLETE_RESPONSE','INCOMPLETE_RESPONSE','TRANSPORT_INTERRUPTED','PARSE_FAILED')
            AND response_fingerprint ~ '^[0-9a-f]{64}$' AND octet_length(response_prefix)<=128000
            AND (response_kind<>'COMPLETE_RESPONSE' OR octet_length(response_prefix)=0)
            AND (response_body_sha256 IS NULL OR
                 (response_kind='COMPLETE_RESPONSE' AND response_body_sha256 ~ '^[0-9a-f]{64}$'))
            AND (captured_result='VERIFIED' AND captured_failure_reason IS NULL AND response_kind='COMPLETE_RESPONSE'
                OR captured_result='INCOMPLETE' AND captured_failure_reason IN ('INCOMPLETE_HTTP_RESPONSE','OVERLAP_CHECK_INCOMPLETE')
                OR captured_result='REVISED' AND captured_failure_reason IN
                    ('DATA_REVISION_DETECTED','INCOMPLETE_HTTP_RESPONSE','OVERLAP_CHECK_INCOMPLETE'))
            AND (NOT response_retryable OR captured_result='INCOMPLETE' AND response_kind IN ('PARSE_FAILED','COMPLETE_RESPONSE'))
            AND (captured_result='REVISED' AND isfinite(captured_revision_at)
                 AND revised_open_time IN (first_open_time,last_open_time)
                 AND original_bar_sha256 ~ '^[0-9a-f]{64}$' AND revised_bar_sha256 ~ '^[0-9a-f]{64}$'
                 AND original_bar_sha256<>revised_bar_sha256
                OR captured_result<>'REVISED' AND
                   num_nonnulls(captured_revision_at,revised_open_time,original_bar_sha256,revised_bar_sha256)=0)
            AND (finished_at IS NULL OR (result=captured_result
                 AND failure_reason IS NOT DISTINCT FROM captured_failure_reason)))
    ) IS TRUE);

CREATE INDEX idx_continuous_overlap_unfinished
    ON continuous_sim_overlap_checks(paper_run_id) WHERE finished_at IS NULL;

CREATE OR REPLACE FUNCTION nq_guard_continuous_overlap_check() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR OLD.finished_at IS NOT NULL
        OR (OLD.check_id,OLD.paper_run_id,OLD.first_open_time,OLD.last_open_time,OLD.retry,OLD.started_at)
           IS DISTINCT FROM
           (NEW.check_id,NEW.paper_run_id,NEW.first_open_time,NEW.last_open_time,NEW.retry,NEW.started_at)
        OR (OLD.response_captured_at IS NULL AND (NEW.response_captured_at IS NULL OR NEW.finished_at IS NOT NULL))
        OR (OLD.response_captured_at IS NOT NULL AND (NEW.finished_at IS NULL
            OR (OLD.response_kind,OLD.response_fingerprint,OLD.response_prefix,OLD.response_body_sha256,OLD.response_captured_at,
                OLD.captured_result,OLD.captured_failure_reason,OLD.response_retryable,OLD.captured_revision_at,
                OLD.revised_open_time,OLD.original_bar_sha256,OLD.revised_bar_sha256) IS DISTINCT FROM
               (NEW.response_kind,NEW.response_fingerprint,NEW.response_prefix,NEW.response_body_sha256,NEW.response_captured_at,
                NEW.captured_result,NEW.captured_failure_reason,NEW.response_retryable,NEW.captured_revision_at,
                NEW.revised_open_time,NEW.original_bar_sha256,NEW.revised_bar_sha256)))
    THEN
        RAISE EXCEPTION 'CONTINUOUS_SIM_OVERLAP_CHECK_IMMUTABLE' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;

ALTER TABLE continuous_sim_runs
    ADD COLUMN terminal_intent_id uuid,
    ADD COLUMN terminal_intent_source varchar(24),
    ADD CONSTRAINT uq_continuous_terminal_intent UNIQUE (terminal_intent_id),
    ADD CONSTRAINT uq_continuous_run_terminal_intent UNIQUE (paper_run_id,terminal_intent_id),
    ADD CONSTRAINT ck_continuous_terminal_intent CHECK ((
        (terminal_intent_id IS NULL AND terminal_intent_source IS NULL)
        OR (terminal_intent_id IS NOT NULL AND end_at IS NOT NULL AND
            (terminal_intent_source IN ('MANUAL_END','PAPER_RUN_STOP') AND manual_end_at IS NOT NULL
             OR terminal_intent_source IN ('AUTOMATIC_DEADLINE','AUTOMATIC_BLOCK')
                AND manual_end_at IS NULL AND status='STOPPED'))
    ) IS TRUE);

ALTER TABLE continuous_sim_operations
    ADD COLUMN terminal_intent_id uuid,
    ADD CONSTRAINT fk_continuous_operation_terminal_intent FOREIGN KEY (paper_run_id,terminal_intent_id)
        REFERENCES continuous_sim_runs(paper_run_id,terminal_intent_id),
    ADD CONSTRAINT ck_continuous_operation_terminal_intent CHECK (terminal_intent_id IS NULL OR operation_type='END');

CREATE FUNCTION nq_guard_continuous_terminal_intent() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.terminal_intent_id IS NOT NULL AND
       (OLD.terminal_intent_id,OLD.terminal_intent_source)
          IS DISTINCT FROM (NEW.terminal_intent_id,NEW.terminal_intent_source)
    THEN
        RAISE EXCEPTION 'CONTINUOUS_SIM_IMMUTABLE_TERMINAL_INTENT' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_continuous_terminal_intent BEFORE UPDATE ON continuous_sim_runs
    FOR EACH ROW EXECUTE FUNCTION nq_guard_continuous_terminal_intent();

-- 同一游标行锁内只允许首次停止写入身份。后续 END 回执关联这份意图。
CREATE FUNCTION nq_request_continuous_end(target_run text, requested_at timestamptz, operation_id uuid)
RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE bound continuous_sim_runs%ROWTYPE;
DECLARE ended_request_at timestamptz;
BEGIN
    SELECT * INTO bound FROM continuous_sim_runs
        WHERE paper_run_id=target_run AND end_at IS NOT NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    IF bound.status<>'STOPPED' AND bound.manual_end_at IS NULL THEN
        ended_request_at := clock_timestamp();
        UPDATE continuous_sim_runs SET manual_end_at=ended_request_at,
            terminal_intent_id=coalesce(operation_id,gen_random_uuid()),
            terminal_intent_source=CASE WHEN operation_id IS NULL THEN 'PAPER_RUN_STOP' ELSE 'MANUAL_END' END,
            lifecycle_status='ADMISSION_CLOSED',updated_at=ended_request_at WHERE paper_run_id=target_run;
    END IF;
    RETURN true;
END;
$$;

CREATE OR REPLACE FUNCTION nq_request_continuous_end(target_run text, requested_at timestamptz)
RETURNS boolean LANGUAGE sql AS $$
    SELECT nq_request_continuous_end(target_run,requested_at,NULL::uuid)
$$;

-- V56 的插入策略会回读原恢复回执。将账户关联放进标量函数，避免策略重写时展开自引用。
CREATE FUNCTION nq_sim_projection_receipt_matches_run(target_run text, target_account bigint,
    target_tenant uuid, target_actor text) RETURNS boolean
LANGUAGE sql STABLE SET search_path=pg_catalog,public,pg_temp AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.paper_trading_runs p JOIN public.accounts a ON a.account_id=p.canonical_account_id
        WHERE p.paper_run_id=target_run AND a.account_id=target_account
          AND p.tenant_id=target_tenant AND a.tenant_id=p.tenant_id
          AND p.trade_env='SIM' AND p.created_by=target_actor
    )
$$;

DROP POLICY sim_projection_operations_read ON sim_account_projection_operations;
CREATE POLICY sim_projection_operations_read ON sim_account_projection_operations FOR SELECT
    USING (nq_tenant_is_current(tenant_id) AND
        (nq_is_relation_owner('sim_account_projection_operations')
         OR owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint
            AND nq_sim_projection_receipt_matches_run(paper_run_id,account_id,tenant_id,actor)));

-- 轮询器只需了解该运行是否已有恢复回执，不读取 OWNER 的详细数据。
CREATE FUNCTION nq_sim_projection_recheck_required(target_run text) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog,public,pg_temp AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.sim_account_projection_operations r
        WHERE r.paper_run_id=target_run AND r.recovery_operation_id IS NULL
          AND public.nq_tenant_is_current(r.tenant_id)
    )
$$;
REVOKE ALL ON FUNCTION nq_sim_projection_recheck_required(text) FROM PUBLIC;

COMMENT ON COLUMN continuous_sim_overlap_checks.response_prefix IS 'HTTP 未完成或无法解析时保留的有界原字节，与核验尝试同一身份';
COMMENT ON COLUMN continuous_sim_runs.terminal_intent_id IS '首次接受的运行终止意图，历史无法证明的身份保持空值';
COMMENT ON FUNCTION nq_sim_projection_recheck_required(text) IS '当前租户内按运行返回是否需要复核，运行角色无法借此读取回执明细';
