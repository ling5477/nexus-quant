SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='120s';

ALTER TABLE scheduled_job_controls DROP CONSTRAINT scheduled_job_controls_job_key_check;
ALTER TABLE scheduled_job_controls ADD CONSTRAINT scheduled_job_controls_job_key_check CHECK(job_key IN (
 'CONTINUOUS_SIM_POLL','PAPER_MATCHING','STRATEGY_RECOVERY','LEDGER_RECONCILIATION','OKX_RECOVERY',
 'OKX_RECONCILIATION','BINANCE_RECONCILIATION','VALIDATION_EVIDENCE_REFRESH','STRATEGY_EXPERIMENTS'));

ALTER TABLE research_creation_operations DROP CONSTRAINT research_creation_operations_kind_check;
ALTER TABLE research_creation_operations ADD CONSTRAINT research_creation_operations_kind_check
 CHECK(kind IN ('STRATEGY','VERSION','RESEARCH','BACKTEST','BACKTEST_RUN','SIM_RUN','PAPER_RUN','EXPERIMENT'));

CREATE TABLE strategy_experiments (
 experiment_id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL DEFAULT nq_current_tenant_id() REFERENCES tenants(tenant_id),
 owner_user_id bigint NOT NULL REFERENCES users(id),
 strategy_type varchar(64) NOT NULL CHECK(strategy_type IN ('SPOT_SMA_TARGET_V1','SPOT_CHANNEL_BREAKOUT_V1')),
 frozen_input jsonb NOT NULL CHECK(jsonb_typeof(frozen_input)='object' AND octet_length(frozen_input::text)<=2097152),
 trial_count integer NOT NULL CHECK(trial_count BETWEEN 1 AND 16),
 concurrency_limit integer NOT NULL DEFAULT 1 CHECK(concurrency_limit=1),
 cancel_requested boolean NOT NULL DEFAULT false,
 active boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_strategy_experiments_owner ON strategy_experiments(tenant_id,owner_user_id,created_at DESC,experiment_id);
COMMENT ON TABLE strategy_experiments IS '固定输入的策略参数实验；只编排正式Java回测，不授予交易或发布权限';
COMMENT ON COLUMN strategy_experiments.frozen_input IS '创建时冻结的数据内容、摘要、时间范围、资金、成本和执行假设；禁止静默修改';
COMMENT ON COLUMN strategy_experiments.concurrency_limit IS '现有Dispatcher串行资源合同，每组实际最多一个执行，低于建议上限二';

CREATE TABLE strategy_experiment_trials (
 trial_id uuid PRIMARY KEY,
 experiment_id uuid NOT NULL REFERENCES strategy_experiments(experiment_id),
 ordinal integer NOT NULL CHECK(ordinal BETWEEN 1 AND 16),
 parameter_identity varchar(512) NOT NULL,
 parameters jsonb NOT NULL CHECK(jsonb_typeof(parameters)='object'),
 strategy_version_id varchar(64) NOT NULL REFERENCES strategy_versions(strategy_version_id),
 UNIQUE(experiment_id,ordinal), UNIQUE(experiment_id,parameter_identity)
);
COMMENT ON TABLE strategy_experiment_trials IS '稳定试验和不可变参数/正式策略版本身份';
CREATE TABLE strategy_experiment_attempts (
 attempt_id uuid PRIMARY KEY,
 trial_id uuid NOT NULL REFERENCES strategy_experiment_trials(trial_id),
 attempt integer NOT NULL CHECK(attempt BETWEEN 1 AND 3),
 backtest_config_id varchar(64) NOT NULL UNIQUE REFERENCES backtest_configs(backtest_config_id),
 backtest_run_id varchar(64) NOT NULL UNIQUE REFERENCES backtest_runs(backtest_run_id),
 status varchar(24) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED','OUTCOME_UNKNOWN')),
 failure_reason varchar(128),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(trial_id,attempt)
);
CREATE INDEX idx_strategy_attempts_pending ON strategy_experiment_attempts(status,created_at,attempt_id)
 WHERE status IN ('QUEUED','RUNNING');
COMMENT ON TABLE strategy_experiment_attempts IS '每次尝试独立Run及配置，保留失败；未知结果先查原Run，不以新身份自动重跑';

ALTER TABLE strategy_experiments ENABLE ROW LEVEL SECURITY;
ALTER TABLE strategy_experiments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON strategy_experiments USING(nq_tenant_is_current(tenant_id))
 WITH CHECK(nq_tenant_is_current(tenant_id) AND EXISTS(SELECT 1 FROM tenant_memberships m WHERE m.tenant_id=strategy_experiments.tenant_id AND m.user_id=strategy_experiments.owner_user_id AND m.status='ACTIVE'));
ALTER TABLE strategy_experiment_trials ENABLE ROW LEVEL SECURITY;
ALTER TABLE strategy_experiment_trials FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON strategy_experiment_trials USING(EXISTS(SELECT 1 FROM strategy_experiments e WHERE e.experiment_id=strategy_experiment_trials.experiment_id))
 WITH CHECK(EXISTS(SELECT 1 FROM strategy_experiments e WHERE e.experiment_id=strategy_experiment_trials.experiment_id));
ALTER TABLE strategy_experiment_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE strategy_experiment_attempts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON strategy_experiment_attempts USING(EXISTS(SELECT 1 FROM strategy_experiment_trials t WHERE t.trial_id=strategy_experiment_attempts.trial_id))
 WITH CHECK(EXISTS(SELECT 1 FROM strategy_experiment_trials t WHERE t.trial_id=strategy_experiment_attempts.trial_id));

CREATE FUNCTION nq_check_experiment_links() RETURNS trigger LANGUAGE plpgsql SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'EXPERIMENT_HISTORY_IMMUTABLE' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (
   (TG_TABLE_NAME='strategy_experiments' AND (to_jsonb(NEW)-ARRAY['cancel_requested','active']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['cancel_requested','active']))
   OR TG_TABLE_NAME='strategy_experiment_trials'
   OR (TG_TABLE_NAME='strategy_experiment_attempts' AND (to_jsonb(NEW)-ARRAY['status','failure_reason']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','failure_reason']))) THEN
   RAISE EXCEPTION 'EXPERIMENT_INPUT_IMMUTABLE' USING ERRCODE='23514';
 END IF;
 IF TG_TABLE_NAME='strategy_experiment_trials' THEN
  IF NOT EXISTS(
   SELECT 1 FROM strategy_versions v JOIN strategy_experiments e ON e.tenant_id=v.tenant_id
   WHERE v.strategy_version_id=NEW.strategy_version_id AND e.experiment_id=NEW.experiment_id
    AND v.param_snapshot_json=NEW.parameters) THEN
   RAISE EXCEPTION 'EXPERIMENT_VERSION_MISMATCH' USING ERRCODE='23514';
  END IF;
 ELSIF TG_TABLE_NAME='strategy_experiment_attempts' THEN
  IF NOT EXISTS(
   SELECT 1 FROM strategy_experiment_trials t JOIN backtest_runs r ON r.strategy_version_id=t.strategy_version_id
   WHERE t.trial_id=NEW.trial_id AND r.backtest_run_id=NEW.backtest_run_id AND r.backtest_config_id=NEW.backtest_config_id) THEN
   RAISE EXCEPTION 'EXPERIMENT_RUN_MISMATCH' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_experiment_links BEFORE INSERT OR UPDATE OR DELETE ON strategy_experiments FOR EACH ROW EXECUTE FUNCTION nq_check_experiment_links();
CREATE TRIGGER trg_experiment_trial_links BEFORE INSERT OR UPDATE OR DELETE ON strategy_experiment_trials FOR EACH ROW EXECUTE FUNCTION nq_check_experiment_links();
CREATE TRIGGER trg_experiment_attempt_links BEFORE INSERT OR UPDATE OR DELETE ON strategy_experiment_attempts FOR EACH ROW EXECUTE FUNCTION nq_check_experiment_links();

-- 绑定实验后禁止单次回测HTTP入口与后台抢跑；仍调用原执行服务，只增加编排所有权边界。
CREATE FUNCTION nq_guard_experiment_run() RETURNS trigger LANGUAGE plpgsql SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
 IF NEW.status IS DISTINCT FROM OLD.status AND EXISTS(SELECT 1 FROM strategy_experiment_attempts a
   WHERE a.backtest_run_id=NEW.backtest_run_id AND a.attempt_id::text IS DISTINCT FROM current_setting('nq.experiment_attempt',true)) THEN
   RAISE EXCEPTION 'EXPERIMENT_RUN_MANAGED' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_experiment_run_guard BEFORE UPDATE OF status ON backtest_runs FOR EACH ROW EXECUTE FUNCTION nq_guard_experiment_run();

ALTER TABLE tenant_background_work ADD COLUMN experiment_id uuid REFERENCES strategy_experiments(experiment_id);
ALTER TABLE tenant_background_work DROP CONSTRAINT ck_tenant_work_kind;
ALTER TABLE tenant_background_work ADD CONSTRAINT ck_tenant_work_kind CHECK(work_kind IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT','EXPERIMENT'));
ALTER TABLE tenant_background_work DROP CONSTRAINT ck_tenant_work_identity;
ALTER TABLE tenant_background_work ADD CONSTRAINT ck_tenant_work_identity CHECK(
 (work_kind='EXPERIMENT' AND work_id=experiment_id::text AND num_nonnulls(paper_run_id,strategy_run_id,order_id,strategy_schedule_id,paper_schedule_id,account_id)=0)
 OR (experiment_id IS NULL AND ((work_kind='ACCOUNT' AND account_id IS NOT NULL AND work_id=account_id::text
 AND num_nonnulls(paper_run_id,strategy_run_id,order_id,strategy_schedule_id,paper_schedule_id)=0)
 OR (work_kind<>'ACCOUNT' AND num_nonnulls(paper_run_id,strategy_run_id,order_id,strategy_schedule_id,paper_schedule_id)=1
 AND ((work_kind='PAPER' AND work_id=paper_run_id) OR (work_kind='STRATEGY' AND work_id=strategy_run_id)
 OR (work_kind='ORDER' AND work_id=order_id) OR (work_kind='STRATEGY_SCHEDULE' AND work_id=strategy_schedule_id)
 OR (work_kind='PAPER_SCHEDULE' AND work_id=paper_schedule_id))))));
ALTER TABLE tenant_work_scan_cursors DROP CONSTRAINT ck_tenant_scan_kind;
ALTER TABLE tenant_work_scan_cursors ADD CONSTRAINT ck_tenant_scan_kind CHECK(work_kind IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT','EXPERIMENT'));
INSERT INTO tenant_work_scan_cursors(work_kind) VALUES('EXPERIMENT');
CREATE FUNCTION nq_register_experiment_work() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
 INSERT INTO tenant_background_work(work_kind,work_id,tenant_id,experiment_id,eligible)
 VALUES('EXPERIMENT',NEW.experiment_id::text,NEW.tenant_id,NEW.experiment_id,NEW.active)
 ON CONFLICT(work_kind,work_id) DO UPDATE SET eligible=EXCLUDED.eligible;
 RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION nq_register_experiment_work() FROM PUBLIC;
CREATE TRIGGER trg_register_experiment_work AFTER INSERT OR UPDATE OF active ON strategy_experiments FOR EACH ROW EXECUTE FUNCTION nq_register_experiment_work();
CREATE OR REPLACE FUNCTION nq_poll_tenant_work(p_kind text,p_limit integer) RETURNS TABLE(work_id text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public, pg_temp AS $$
DECLARE last_id text; ids text[];
BEGIN
    IF p_kind NOT IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT','EXPERIMENT') OR p_limit<1 OR p_limit>64 THEN
        RAISE EXCEPTION 'invalid bounded tenant work scan' USING ERRCODE='22023';
    END IF;
    -- 普通用户业务上下文不具备全局枚举权；调度器从空上下文调用固定的最小身份投影。
    IF nq_current_tenant_id() IS NOT NULL THEN RAISE EXCEPTION 'TENANT_ACCESS_DENIED' USING ERRCODE='42501'; END IF;
    SELECT last_work_id INTO STRICT last_id FROM tenant_work_scan_cursors WHERE work_kind=p_kind FOR UPDATE;
    SELECT array_agg(x.work_id ORDER BY x.work_id) INTO ids FROM (
        SELECT w.work_id FROM tenant_background_work w
        WHERE w.work_kind=p_kind AND w.work_id>last_id AND (p_kind='ACCOUNT' OR (w.eligible AND nq_tenant_background_enabled(w.tenant_id)))
            AND (p_kind<>'PAPER' OR EXISTS (SELECT 1 FROM tenant_continuous_slots s WHERE s.paper_run_id=w.paper_run_id AND s.active))
        ORDER BY w.work_id LIMIT p_limit) x;
    IF ids IS NULL THEN
        SELECT array_agg(x.work_id ORDER BY x.work_id) INTO ids FROM (
            SELECT w.work_id FROM tenant_background_work w WHERE w.work_kind=p_kind AND (p_kind='ACCOUNT' OR (w.eligible
                AND nq_tenant_background_enabled(w.tenant_id)))
                AND (p_kind<>'PAPER' OR EXISTS (SELECT 1 FROM tenant_continuous_slots s WHERE s.paper_run_id=w.paper_run_id AND s.active))
            ORDER BY w.work_id LIMIT p_limit) x;
        IF ids IS NULL THEN
            UPDATE tenant_work_scan_cursors SET last_work_id='',updated_at=clock_timestamp() WHERE work_kind=p_kind;
            RETURN;
        END IF;
    END IF;
    UPDATE tenant_work_scan_cursors SET last_work_id=ids[array_length(ids,1)],updated_at=clock_timestamp() WHERE work_kind=p_kind;
    RETURN QUERY SELECT unnest(ids);
END;
$$;
REVOKE ALL ON FUNCTION nq_poll_tenant_work(text,integer) FROM PUBLIC;

CREATE OR REPLACE FUNCTION nq_restore_tenant_work(p_kind text,p_id text) RETURNS TABLE(tenant_id uuid,execution_epoch bigint)
LANGUAGE plpgsql SECURITY DEFINER SET search_path TO pg_catalog, public, pg_temp AS $$
DECLARE work tenant_background_work%ROWTYPE; control tenant_execution_controls%ROWTYPE; expected text;
BEGIN
    IF p_kind NOT IN ('PAPER','STRATEGY','ORDER','STRATEGY_SCHEDULE','PAPER_SCHEDULE','ACCOUNT','EXPERIMENT') OR length(p_id)>128 THEN
        RAISE EXCEPTION 'RUN_NOT_FOUND' USING ERRCODE='42501';
    END IF;
    SELECT * INTO work FROM tenant_background_work w WHERE w.work_kind=p_kind AND w.work_id=p_id;
    IF NOT FOUND OR (nq_current_tenant_id() IS NOT NULL AND nq_current_tenant_id()<>work.tenant_id) THEN
        RAISE EXCEPTION 'RUN_NOT_FOUND' USING ERRCODE='42501';
    END IF;
    SELECT * INTO STRICT control FROM tenant_execution_controls c WHERE c.tenant_id=work.tenant_id;
    IF (p_kind='ACCOUNT' AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.tenant_id=work.tenant_id AND t.status='ACTIVE'))
        OR (p_kind<>'ACCOUNT' AND NOT nq_tenant_background_enabled(work.tenant_id)) THEN
        RAISE EXCEPTION 'TENANT_SUSPENDED' USING ERRCODE='42501';
    END IF;
    expected := NULLIF(current_setting('nq.execution_epoch',true),'');
    IF p_kind<>'ACCOUNT' AND expected IS NOT NULL AND expected::bigint<>control.execution_epoch THEN
        RAISE EXCEPTION 'TENANT_EXECUTION_EPOCH_STALE' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT work.tenant_id,control.execution_epoch;
END;
$$;
REVOKE ALL ON FUNCTION nq_restore_tenant_work(text,text) FROM PUBLIC;
