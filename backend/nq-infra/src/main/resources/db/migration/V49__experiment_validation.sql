SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='120s';

ALTER TABLE research_creation_operations DROP CONSTRAINT research_creation_operations_kind_check;
ALTER TABLE research_creation_operations ADD CONSTRAINT research_creation_operations_kind_check
 CHECK(kind IN ('STRATEGY','VERSION','RESEARCH','BACKTEST','BACKTEST_RUN','SIM_RUN','PAPER_RUN','EXPERIMENT','EXPERIMENT_VALIDATION'));

CREATE TABLE experiment_validations (
 validation_id uuid PRIMARY KEY,
 tenant_id uuid NOT NULL DEFAULT nq_current_tenant_id() REFERENCES tenants(tenant_id),
 owner_user_id bigint NOT NULL REFERENCES users(id),
 source_experiment_id uuid NOT NULL REFERENCES strategy_experiments(experiment_id),
 frozen_policy jsonb NOT NULL CHECK(jsonb_typeof(frozen_policy)='object' AND octet_length(frozen_policy::text)<=65536),
 status varchar(32) NOT NULL CHECK(status IN ('RUNNING','SUCCEEDED','CANCELLED','NO_ELIGIBLE_CANDIDATE','INSUFFICIENT_DATA','OUTCOME_UNKNOWN','FAILED')),
 failure_reason varchar(128),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX idx_validation_active_source ON experiment_validations(source_experiment_id) WHERE status='RUNNING';
CREATE INDEX idx_validation_owner ON experiment_validations(tenant_id,owner_user_id,source_experiment_id,created_at DESC);
COMMENT ON TABLE experiment_validations IS '源实验上的时间顺序验证身份；候选、数据摘要、选择、成本及窗口政策一次冻结';
COMMENT ON COLUMN experiment_validations.frozen_policy IS '固定三折与最终训练/留出区间，独立资金及FLAT重置；短样本不授予交易权限';

CREATE TABLE experiment_validation_steps (
 validation_id uuid NOT NULL REFERENCES experiment_validations(validation_id),
 step_number integer NOT NULL CHECK(step_number BETWEEN 0 AND 15),
 fold_number integer NOT NULL CHECK(fold_number BETWEEN 1 AND 4),
 role varchar(24) NOT NULL CHECK(role IN ('IN_SAMPLE','OUT_OF_SAMPLE','FINAL_HOLDOUT','FEE_STRESS','SLIPPAGE_STRESS')),
 start_bar integer NOT NULL CHECK(start_bar>=0),
 end_bar integer NOT NULL CHECK(end_bar>start_bar AND end_bar<=720),
 experiment_id uuid UNIQUE REFERENCES strategy_experiments(experiment_id),
 unavailable_reason varchar(128),
 selection jsonb CHECK(selection IS NULL OR jsonb_typeof(selection)='object'),
 selected_at timestamptz,
 PRIMARY KEY(validation_id,step_number),
 CHECK((experiment_id IS NULL)=(unavailable_reason IS NOT NULL)),
 CHECK((selection IS NULL)=(selected_at IS NULL)),
 CHECK(selection IS NULL OR role='IN_SAMPLE')
);
COMMENT ON TABLE experiment_validation_steps IS '有界验证步骤引用原实验试验与尝试体系，不复制回测事实；训练选择只能冻结一次';
COMMENT ON COLUMN experiment_validation_steps.selection IS '只从同折训练评价排序，绑定原Trial、版本、所有候选得分与固定tie-break';

CREATE TABLE experiment_holdout_views (
 view_id uuid PRIMARY KEY,
 validation_id uuid NOT NULL REFERENCES experiment_validations(validation_id),
 viewed_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_holdout_views_validation ON experiment_holdout_views(validation_id,viewed_at);
COMMENT ON TABLE experiment_holdout_views IS '显式查看留出结果的追加记录；刷新只读，重放同查看身份不重复写入';

ALTER TABLE experiment_validations ENABLE ROW LEVEL SECURITY;
ALTER TABLE experiment_validations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON experiment_validations USING(nq_tenant_is_current(tenant_id))
 WITH CHECK(nq_tenant_is_current(tenant_id) AND EXISTS(SELECT 1 FROM strategy_experiments e
 WHERE e.experiment_id=source_experiment_id AND e.tenant_id=experiment_validations.tenant_id AND e.owner_user_id=experiment_validations.owner_user_id));
ALTER TABLE experiment_validation_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE experiment_validation_steps FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON experiment_validation_steps USING(EXISTS(SELECT 1 FROM experiment_validations v WHERE v.validation_id=experiment_validation_steps.validation_id))
 WITH CHECK(EXISTS(SELECT 1 FROM experiment_validations v WHERE v.validation_id=experiment_validation_steps.validation_id));
ALTER TABLE experiment_holdout_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE experiment_holdout_views FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_scope ON experiment_holdout_views USING(EXISTS(SELECT 1 FROM experiment_validations v WHERE v.validation_id=experiment_holdout_views.validation_id))
 WITH CHECK(EXISTS(SELECT 1 FROM experiment_validations v WHERE v.validation_id=experiment_holdout_views.validation_id));

CREATE FUNCTION nq_guard_validation_history() RETURNS trigger LANGUAGE plpgsql SET search_path TO pg_catalog,public,pg_temp AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'VALIDATION_HISTORY_IMMUTABLE' USING ERRCODE='23514'; END IF;
 IF TG_TABLE_NAME='experiment_validations' THEN
  IF TG_OP='UPDATE' AND (to_jsonb(NEW)-ARRAY['status','failure_reason']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','failure_reason']) THEN
   RAISE EXCEPTION 'VALIDATION_POLICY_IMMUTABLE' USING ERRCODE='23514';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM strategy_experiments e WHERE e.experiment_id=NEW.source_experiment_id
   AND e.tenant_id=NEW.tenant_id AND e.owner_user_id=NEW.owner_user_id AND NOT(e.frozen_input ? 'validationId')) THEN
   RAISE EXCEPTION 'VALIDATION_SOURCE_MISMATCH' USING ERRCODE='23514'; END IF;
 ELSIF TG_TABLE_NAME='experiment_validation_steps' THEN
  IF TG_OP='UPDATE' AND (OLD.selection IS NOT NULL OR (to_jsonb(NEW)-ARRAY['selection','selected_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['selection','selected_at'])) THEN
   RAISE EXCEPTION 'VALIDATION_STEP_IMMUTABLE' USING ERRCODE='23514'; END IF;
  IF NEW.experiment_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM strategy_experiments e JOIN experiment_validations v ON v.validation_id=NEW.validation_id
   WHERE e.experiment_id=NEW.experiment_id AND e.tenant_id=v.tenant_id AND e.owner_user_id=v.owner_user_id
    AND e.frozen_input->>'validationId'=v.validation_id::text
    AND (e.frozen_input->'executionSpec'->'windowExecution'->>'startBar')::integer=NEW.start_bar
    AND (e.frozen_input->'executionSpec'->'windowExecution'->>'endBar')::integer=NEW.end_bar) THEN
   RAISE EXCEPTION 'VALIDATION_STEP_SCOPE_MISMATCH' USING ERRCODE='23514'; END IF;
 ELSE
  IF TG_OP='UPDATE' THEN RAISE EXCEPTION 'HOLDOUT_VIEW_IMMUTABLE' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_validation_history BEFORE INSERT OR UPDATE OR DELETE ON experiment_validations FOR EACH ROW EXECUTE FUNCTION nq_guard_validation_history();
CREATE TRIGGER trg_validation_step_history BEFORE INSERT OR UPDATE OR DELETE ON experiment_validation_steps FOR EACH ROW EXECUTE FUNCTION nq_guard_validation_history();
CREATE TRIGGER trg_holdout_view_history BEFORE INSERT OR UPDATE OR DELETE ON experiment_holdout_views FOR EACH ROW EXECUTE FUNCTION nq_guard_validation_history();
