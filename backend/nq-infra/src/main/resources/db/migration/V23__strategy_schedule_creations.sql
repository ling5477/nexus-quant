-- 原创建编号、回执和审计与正式计划同事务提交；V1—V22保持原样，不回填虚构的旧回执。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE strategy_schedule_creations (
    creation_id uuid PRIMARY KEY CHECK(creation_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor varchar(128) NOT NULL,
    request_version integer NOT NULL CHECK(request_version=1),
    input_sha256 char(64) NOT NULL CHECK(input_sha256 ~ '^[a-f0-9]{64}$'),
    request_json jsonb NOT NULL,
    schedule_job_id varchar(128) NOT NULL UNIQUE REFERENCES strategy_schedules(schedule_job_id) ON DELETE RESTRICT,
    result_json jsonb NOT NULL,
    trace_id varchar(64) NOT NULL,
    completed_at timestamptz NOT NULL,
    CONSTRAINT chk_strategy_schedule_creation_identity CHECK(owner_user_id>0 AND btrim(actor)<>'' AND btrim(trace_id)<>'' AND isfinite(completed_at)),
    CONSTRAINT chk_strategy_schedule_creation_request CHECK((
        jsonb_typeof(request_json)='object' AND octet_length(request_json::text)<=32768
        AND jsonb_typeof(request_json->'strategyId')='string' AND length(request_json->>'strategyId') BETWEEN 1 AND 128
        AND request_json->>'scheduleType'='CRON'
        AND jsonb_typeof(request_json->'cronExpr')='string' AND length(request_json->>'cronExpr') BETWEEN 1 AND 128
        AND jsonb_typeof(request_json->'timezone')='string' AND length(request_json->>'timezone') BETWEEN 1 AND 64
        AND jsonb_typeof(request_json->'enabled')='boolean'
        AND jsonb_typeof(request_json->'windowConfig')='string' AND octet_length(request_json->>'windowConfig')<=8192
        AND request_json->>'dedupScope' IN ('SCHEDULE_WINDOW','REQUEST','STRATEGY')
    ) IS TRUE),
    CONSTRAINT chk_strategy_schedule_creation_result CHECK((jsonb_typeof(result_json)='object'
        AND octet_length(result_json::text)<=65536 AND result_json->>'scheduleJobId'=schedule_job_id) IS TRUE)
);

CREATE FUNCTION validate_strategy_schedule_creation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_schedule strategy_schedules%ROWTYPE;
BEGIN
    SELECT * INTO v_schedule FROM strategy_schedules WHERE schedule_job_id=NEW.schedule_job_id FOR SHARE;
    IF NOT FOUND THEN RAISE EXCEPTION 'canonical schedule missing' USING ERRCODE='23503'; END IF;
    IF NEW.request_json->>'strategyId' IS DISTINCT FROM v_schedule.strategy_id
       OR NEW.request_json->>'scheduleType' IS DISTINCT FROM v_schedule.schedule_type
       OR NEW.request_json->>'cronExpr' IS DISTINCT FROM v_schedule.cron_expr
       OR NEW.request_json->>'timezone' IS DISTINCT FROM v_schedule.timezone
       OR (NEW.request_json->>'enabled')::boolean IS DISTINCT FROM v_schedule.enabled
       OR (NEW.request_json->>'windowConfig')::jsonb IS DISTINCT FROM v_schedule.window_config
       OR NEW.request_json->>'dedupScope' IS DISTINCT FROM v_schedule.dedup_scope
       OR NEW.result_json->>'scheduleJobId' IS DISTINCT FROM v_schedule.schedule_job_id
       OR NEW.result_json->>'strategyId' IS DISTINCT FROM v_schedule.strategy_id
       OR NEW.result_json->>'scheduleType' IS DISTINCT FROM v_schedule.schedule_type
       OR NEW.result_json->>'cronExpr' IS DISTINCT FROM v_schedule.cron_expr
       OR NEW.result_json->>'timezone' IS DISTINCT FROM v_schedule.timezone
       OR (NEW.result_json->>'enabled')::boolean IS DISTINCT FROM v_schedule.enabled
       OR (NEW.result_json->>'windowConfig')::jsonb IS DISTINCT FROM v_schedule.window_config
       OR NEW.result_json->>'dedupScope' IS DISTINCT FROM v_schedule.dedup_scope
       OR NEW.result_json->>'exchangeCode' IS DISTINCT FROM v_schedule.exchange_code
       OR (NEW.result_json->>'accountId')::bigint IS DISTINCT FROM v_schedule.account_id
       OR NEW.result_json->>'tradeEnv' IS DISTINCT FROM v_schedule.trade_env
       OR (NEW.result_json->>'lastTriggeredAt')::timestamptz IS DISTINCT FROM v_schedule.last_triggered_at
       OR (NEW.result_json->>'createdAt')::timestamptz IS DISTINCT FROM v_schedule.created_at
       OR (NEW.result_json->>'updatedAt')::timestamptz IS DISTINCT FROM v_schedule.updated_at
    THEN RAISE EXCEPTION 'schedule creation input or snapshot does not match canonical plan' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_strategy_schedule_creation_validate BEFORE INSERT ON strategy_schedule_creations
    FOR EACH ROW EXECUTE FUNCTION validate_strategy_schedule_creation();

CREATE FUNCTION preserve_strategy_schedule_creation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'original schedule creation receipt is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER trg_strategy_schedule_creation_immutable BEFORE UPDATE OR DELETE ON strategy_schedule_creations
    FOR EACH ROW EXECUTE FUNCTION preserve_strategy_schedule_creation();

COMMENT ON TABLE strategy_schedule_creations IS '正式计划创建的原编号与不可变回执；只记录同事务成功创建，不把404当作未曾执行的证明';
COMMENT ON COLUMN strategy_schedule_creations.creation_id IS '客户端发送前保存的原UUID；同号同输入返回原结果，另一个编号表示另一次明确创建意图';
COMMENT ON COLUMN strategy_schedule_creations.owner_user_id IS '服务端认证用户ID；包括其他管理员均不能查询本用户的原创建回执';
COMMENT ON COLUMN strategy_schedule_creations.actor IS '服务端认证用户名，不接受客户端指定操作者';
COMMENT ON COLUMN strategy_schedule_creations.request_version IS '创建输入身份协议版本，与开发批次无关；当前为1';
COMMENT ON COLUMN strategy_schedule_creations.input_sha256 IS '版本、主体与全部规范化输入的UTF8长度前缀SHA256；窗口JSON原字符串参与身份';
COMMENT ON COLUMN strategy_schedule_creations.request_json IS '原创建输入，窗口JSON保留字符串，不用当前表单或当前策略定义回填';
COMMENT ON COLUMN strategy_schedule_creations.schedule_job_id IS '唯一关联原正式strategy_schedules计划，不新增第二套计划事实源';
COMMENT ON COLUMN strategy_schedule_creations.result_json IS '回读持久化精度后的创建快照；后续启停不得改写原结果';
COMMENT ON COLUMN strategy_schedule_creations.trace_id IS '原创建与同事务完成审计的追踪身份';
COMMENT ON COLUMN strategy_schedule_creations.completed_at IS '本地创建事务的完成时刻，不表示调度已扫描、策略已运行或交易已执行';
COMMENT ON FUNCTION validate_strategy_schedule_creation() IS '提交前核对原输入和回执与正式计划，防止错误账户、环境或计划身份落入回执';
COMMENT ON FUNCTION preserve_strategy_schedule_creation() IS '禁止更新或删除原回执，避免当前配置覆盖原创建结果';
