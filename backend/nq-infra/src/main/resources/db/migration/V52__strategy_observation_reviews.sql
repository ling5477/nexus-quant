SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

ALTER TABLE research_creation_operations DROP CONSTRAINT research_creation_operations_kind_check;
ALTER TABLE research_creation_operations ADD CONSTRAINT research_creation_operations_kind_check
 CHECK(kind IN ('STRATEGY','VERSION','RESEARCH','BACKTEST','BACKTEST_RUN','SIM_RUN','PAPER_RUN','EXPERIMENT',
 'EXPERIMENT_VALIDATION','OBSERVATION','OBSERVATION_REVIEW'));
COMMENT ON COLUMN research_creation_operations.kind IS '正式本地研究创建及观察/追加复盘的稳定回执；不授予发布或交易执行权限';
COMMENT ON COLUMN research_creation_operations.parent_key IS '版本创建绑定父策略；观察绑定原paperRunId，复盘绑定原observationId，其余沿用原合同';

CREATE TABLE strategy_observations (
 observation_id uuid PRIMARY KEY,
 operation_id uuid NOT NULL UNIQUE REFERENCES research_creation_operations(operation_id),
 tenant_id uuid NOT NULL DEFAULT nq_current_tenant_id() REFERENCES tenants(tenant_id),
 owner_user_id bigint NOT NULL REFERENCES users(id),
 paper_run_id varchar(64) NOT NULL REFERENCES paper_trading_runs(paper_run_id),
 canonical_account_id bigint NOT NULL REFERENCES accounts(account_id),
 strategy_version_id varchar(128) NOT NULL REFERENCES strategy_versions(strategy_version_id),
 publish_id varchar(128) NOT NULL REFERENCES backtest_publish_records(publish_record_id),
 validation_id uuid NOT NULL REFERENCES experiment_validations(validation_id),
 selected_trial_id uuid NOT NULL REFERENCES strategy_experiment_trials(trial_id),
 strategy_checksum varchar(64) NOT NULL CHECK(strategy_checksum ~ '^[0-9a-f]{64}$'),
 kind varchar(24) NOT NULL CHECK(kind IN ('FORWARD','RETROSPECTIVE')),
 observation_from timestamptz NOT NULL CHECK(isfinite(observation_from)),
 observation_to timestamptz NOT NULL CHECK(isfinite(observation_to)),
 time_basis varchar(32) NOT NULL CHECK(time_basis='ACTUAL_EVENT_TIME'),
 status varchar(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','CLOSED')),
 revision bigint NOT NULL DEFAULT 0 CHECK(revision BETWEEN 0 AND 10000),
 registered_at timestamptz NOT NULL CHECK(isfinite(registered_at)),
 supersedes_observation_id uuid REFERENCES strategy_observations(observation_id),
 policy jsonb NOT NULL CHECK(jsonb_typeof(policy)='object' AND octet_length(policy::text)<=16384),
 CHECK(observation_to-observation_from BETWEEN interval '1 day' AND interval '90 days'),
 CHECK(date_trunc('hour',observation_from AT TIME ZONE 'UTC')=observation_from AT TIME ZONE 'UTC'),
 CHECK(date_trunc('hour',observation_to AT TIME ZONE 'UTC')=observation_to AT TIME ZONE 'UTC'),
 CHECK((kind='RETROSPECTIVE' AND observation_to<=registered_at)
    OR (kind='FORWARD' AND observation_from>=(date_trunc('hour',registered_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')+interval '1 hour'))
);
CREATE INDEX idx_strategy_observations_owner_run ON strategy_observations(tenant_id,owner_user_id,paper_run_id,registered_at DESC,observation_id);
COMMENT ON TABLE strategy_observations IS '已有Continuous SIM的研究观察目标；不拥有执行、资金或绩效事实，不启动或停止SIM';
COMMENT ON COLUMN strategy_observations.operation_id IS '复用正式研究创建回执的稳定请求身份及JSONB幂等比较';
COMMENT ON COLUMN strategy_observations.observation_to IS 'UTC排他结束；登记后的目标不可修改，延长必须新建关联观察';
COMMENT ON COLUMN strategy_observations.registered_at IS '服务端实际登记时间；回溯记录不得冒充预先登记';
COMMENT ON COLUMN strategy_observations.supersedes_observation_id IS '同所有者同运行的后继观察；保留旧目标和评价历史';
COMMENT ON COLUMN strategy_observations.policy IS '固定评价方法版本、canonical来源及研究/执行分离说明';
COMMENT ON COLUMN strategy_observations.revision IS '仅由追加人工复盘推进的CAS版本，不表示经济事实版本';

CREATE TABLE strategy_observation_reviews (
 review_id uuid PRIMARY KEY,
 operation_id uuid NOT NULL UNIQUE REFERENCES research_creation_operations(operation_id),
 observation_id uuid NOT NULL REFERENCES strategy_observations(observation_id),
 revision bigint NOT NULL CHECK(revision BETWEEN 1 AND 10000),
 reviewer_user_id bigint NOT NULL REFERENCES users(id),
 decision varchar(32) NOT NULL CHECK(decision IN ('CONTINUE_OBSERVATION','STOP_OBSERVATION','RETURN_TO_RESEARCH')),
 reason varchar(2000) NOT NULL CHECK(length(btrim(reason)) BETWEEN 1 AND 2000),
 reviewed_at timestamptz NOT NULL CHECK(isfinite(reviewed_at)),
 observed_at timestamptz NOT NULL CHECK(isfinite(observed_at) AND observed_at<=reviewed_at),
 evaluation_checksum varchar(64) NOT NULL CHECK(evaluation_checksum ~ '^[0-9a-f]{64}$'),
 evaluation_json jsonb NOT NULL CHECK(jsonb_typeof(evaluation_json)='object' AND octet_length(evaluation_json::text)<=2097152),
 UNIQUE(observation_id,revision)
);
CREATE INDEX idx_strategy_observation_reviews_history ON strategy_observation_reviews(observation_id,revision DESC);
COMMENT ON TABLE strategy_observation_reviews IS '仅追加的人工研究决定和服务端canonical分析快照；经济事实仍以原表为准';
COMMENT ON COLUMN strategy_observation_reviews.decision IS '停止或返回研究只关闭观察记录，不暂停SIM、不撤单、不平仓';
COMMENT ON COLUMN strategy_observation_reviews.observed_at IS '评价asOf截止；快照不使用此时点之后的已知事实';
COMMENT ON COLUMN strategy_observation_reviews.evaluation_json IS '原分析投影的方法、范围、水位、覆盖与指标快照；不是另一套权益事实源';
COMMENT ON COLUMN strategy_observation_reviews.evaluation_checksum IS '绑定固定asOf和原事实水位的服务端评价身份';

ALTER TABLE strategy_observations ENABLE ROW LEVEL SECURITY;
ALTER TABLE strategy_observations FORCE ROW LEVEL SECURITY;
CREATE POLICY observation_owner ON strategy_observations USING(
 nq_tenant_is_current(tenant_id) AND owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint)
 WITH CHECK(nq_tenant_is_current(tenant_id) AND owner_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint
 AND EXISTS(SELECT 1 FROM tenant_memberships m JOIN tenants t USING(tenant_id)
 WHERE m.tenant_id=strategy_observations.tenant_id AND m.user_id=owner_user_id AND m.status='ACTIVE'
 AND t.tenant_kind='LOCAL' AND t.status='ACTIVE'));
ALTER TABLE strategy_observation_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE strategy_observation_reviews FORCE ROW LEVEL SECURITY;
CREATE POLICY observation_review_owner ON strategy_observation_reviews USING(EXISTS(
 SELECT 1 FROM strategy_observations o WHERE o.observation_id=strategy_observation_reviews.observation_id))
 WITH CHECK(reviewer_user_id=NULLIF(current_setting('nq.user_id',true),'')::bigint AND EXISTS(
 SELECT 1 FROM strategy_observations o WHERE o.observation_id=strategy_observation_reviews.observation_id
 AND o.owner_user_id=reviewer_user_id));

CREATE FUNCTION nq_guard_strategy_observation_history() RETURNS trigger LANGUAGE plpgsql
SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE parent strategy_observations%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' OR (TG_TABLE_NAME='strategy_observation_reviews' AND TG_OP='UPDATE') THEN
  RAISE EXCEPTION 'OBSERVATION_HISTORY_IMMUTABLE' USING ERRCODE='23514';
 END IF;
 IF TG_TABLE_NAME='strategy_observations' THEN
  IF TG_OP='INSERT' AND (NEW.revision<>0 OR NEW.status<>'OPEN') THEN
   RAISE EXCEPTION 'OBSERVATION_INITIAL_STATE_INVALID' USING ERRCODE='23514'; END IF;
  IF NOT EXISTS(SELECT 1 FROM research_creation_operations op WHERE op.operation_id=NEW.operation_id
   AND nq_tenant_is_current(NEW.tenant_id) AND op.owner_user_id=NEW.owner_user_id
   AND EXISTS(SELECT 1 FROM tenant_memberships m WHERE m.user_id=op.owner_user_id
    AND m.tenant_id=NEW.tenant_id AND m.status='ACTIVE')
   AND op.kind='OBSERVATION' AND op.parent_key=NEW.paper_run_id) THEN
   RAISE EXCEPTION 'OBSERVATION_OPERATION_MISMATCH' USING ERRCODE='23514'; END IF;
  IF TG_OP='UPDATE' THEN
   IF OLD.status<>'OPEN' OR NEW.revision<>OLD.revision+1
    OR (to_jsonb(NEW)-ARRAY['status','revision']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['status','revision']) THEN
    RAISE EXCEPTION 'OBSERVATION_TARGET_IMMUTABLE' USING ERRCODE='23514'; END IF;
  END IF;
  IF NOT EXISTS(SELECT 1 FROM paper_trading_runs p JOIN continuous_sim_runs c USING(paper_run_id)
   JOIN strategy_versions v ON v.strategy_version_id=NEW.strategy_version_id
   JOIN backtest_publish_records b ON b.publish_record_id=NEW.publish_id
   JOIN experiment_validations x ON x.validation_id=NEW.validation_id
   JOIN experiment_validation_steps s ON s.validation_id=x.validation_id AND s.step_number=12
   WHERE p.paper_run_id=NEW.paper_run_id AND p.tenant_id=NEW.tenant_id AND v.tenant_id=NEW.tenant_id
    AND nq_entity_tenant('publication',b.publish_record_id)=NEW.tenant_id
    AND x.tenant_id=NEW.tenant_id AND x.owner_user_id=NEW.owner_user_id
    AND p.canonical_account_id=NEW.canonical_account_id AND p.trade_env='SIM' AND p.publish_id=NEW.publish_id
    AND p.strategy_version_id=NEW.strategy_version_id AND c.strategy_version_id=NEW.strategy_version_id
    AND c.strategy_checksum=NEW.strategy_checksum AND v.checksum=NEW.strategy_checksum
    AND b.strategy_version_id=NEW.strategy_version_id AND b.publish_status='SUCCEEDED' AND x.status='SUCCEEDED'
    AND s.selection->'selected'->>'trialId'=NEW.selected_trial_id::text
    AND s.selection->'selected'->>'versionId'=NEW.strategy_version_id) THEN
   RAISE EXCEPTION 'OBSERVATION_IDENTITY_MISMATCH' USING ERRCODE='23514'; END IF;
  IF NEW.supersedes_observation_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM strategy_observations predecessor
    WHERE predecessor.observation_id=NEW.supersedes_observation_id AND predecessor.owner_user_id=NEW.owner_user_id
     AND predecessor.tenant_id=NEW.tenant_id AND predecessor.paper_run_id=NEW.paper_run_id
     AND predecessor.strategy_version_id=NEW.strategy_version_id AND predecessor.publish_id=NEW.publish_id) THEN
   RAISE EXCEPTION 'OBSERVATION_PREDECESSOR_MISMATCH' USING ERRCODE='23514'; END IF;
 ELSE
  SELECT * INTO STRICT parent FROM strategy_observations WHERE observation_id=NEW.observation_id;
  IF NOT EXISTS(SELECT 1 FROM research_creation_operations op WHERE op.operation_id=NEW.operation_id
   AND nq_tenant_is_current(parent.tenant_id) AND op.owner_user_id=parent.owner_user_id
   AND EXISTS(SELECT 1 FROM tenant_memberships m WHERE m.user_id=op.owner_user_id
    AND m.tenant_id=parent.tenant_id AND m.status='ACTIVE')
   AND op.kind='OBSERVATION_REVIEW' AND op.parent_key=parent.observation_id::text) THEN
   RAISE EXCEPTION 'OBSERVATION_REVIEW_OPERATION_MISMATCH' USING ERRCODE='23514'; END IF;
  IF parent.status<>'OPEN' OR NEW.revision<>parent.revision+1 OR NEW.reviewer_user_id<>parent.owner_user_id
   OR NEW.reviewed_at<parent.registered_at
   OR NEW.evaluation_json->>'paperRunId' IS DISTINCT FROM parent.paper_run_id
   OR NEW.evaluation_json->>'canonicalAccountId' IS DISTINCT FROM parent.canonical_account_id::text
   OR NEW.evaluation_json->>'strategyVersionId' IS DISTINCT FROM parent.strategy_version_id
   OR NEW.evaluation_json->>'publishId' IS DISTINCT FROM parent.publish_id
   OR NEW.evaluation_json->>'timeBasis' IS DISTINCT FROM parent.time_basis
   OR NEW.evaluation_json->>'methodVersion' IS DISTINCT FROM parent.policy->>'methodVersion'
   OR (NEW.evaluation_json->>'observationFrom')::timestamptz IS DISTINCT FROM parent.observation_from
   OR (NEW.evaluation_json->>'observationTo')::timestamptz IS DISTINCT FROM parent.observation_to
   OR (NEW.evaluation_json->>'observedAt')::timestamptz IS DISTINCT FROM NEW.observed_at
   OR NEW.evaluation_json->>'checksum' IS DISTINCT FROM NEW.evaluation_checksum THEN
   RAISE EXCEPTION 'OBSERVATION_REVIEW_SNAPSHOT_MISMATCH' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_strategy_observation_history BEFORE INSERT OR UPDATE OR DELETE ON strategy_observations
 FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_observation_history();
CREATE TRIGGER trg_strategy_observation_review_history BEFORE INSERT OR UPDATE OR DELETE ON strategy_observation_reviews
 FOR EACH ROW EXECUTE FUNCTION nq_guard_strategy_observation_history();

CREATE FUNCTION nq_check_strategy_observation_revision() RETURNS trigger LANGUAGE plpgsql
SET search_path TO pg_catalog,public,pg_temp AS $$
DECLARE parent strategy_observations%ROWTYPE; latest strategy_observation_reviews%ROWTYPE;
BEGIN
 SELECT * INTO STRICT parent FROM strategy_observations WHERE observation_id=NEW.observation_id;
 SELECT * INTO latest FROM strategy_observation_reviews WHERE observation_id=NEW.observation_id ORDER BY revision DESC LIMIT 1;
 IF parent.revision<>coalesce(latest.revision,0) OR parent.status<>(CASE
  WHEN latest.decision IN ('STOP_OBSERVATION','RETURN_TO_RESEARCH') THEN 'CLOSED' ELSE 'OPEN' END) THEN
  RAISE EXCEPTION 'OBSERVATION_REVIEW_REVISION_MISMATCH' USING ERRCODE='23514'; END IF;
 RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER trg_strategy_observation_revision AFTER INSERT OR UPDATE ON strategy_observations
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_check_strategy_observation_revision();
CREATE CONSTRAINT TRIGGER trg_strategy_observation_review_revision AFTER INSERT ON strategy_observation_reviews
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION nq_check_strategy_observation_revision();
