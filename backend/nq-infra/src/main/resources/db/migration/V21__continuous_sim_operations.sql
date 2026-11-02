-- 回执只记录原命令结果；运行、资金和交易事实仍由原 canonical 路径拥有。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE continuous_sim_operations (
    operation_id uuid PRIMARY KEY,
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor varchar(128) NOT NULL,
    operation_type varchar(16) NOT NULL,
    requested_publish_id varchar(128) REFERENCES backtest_publish_records(publish_record_id) ON DELETE RESTRICT,
    requested_budget numeric(16,8),
    paper_run_id varchar(128) NOT NULL REFERENCES continuous_sim_runs(paper_run_id) ON DELETE RESTRICT,
    input_sha256 char(64) NOT NULL,
    trace_id varchar(128) NOT NULL,
    result_json jsonb NOT NULL,
    completed_at timestamptz NOT NULL,
    CONSTRAINT chk_continuous_operation_identity CHECK (
        btrim(actor)<>'' AND btrim(trace_id)<>'' AND input_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT chk_continuous_operation_input CHECK ((
        (operation_type='CREATE' AND requested_publish_id IS NOT NULL
            AND requested_budget>0 AND requested_budget<=100000)
        OR (operation_type IN ('PAUSE','RESUME') AND requested_publish_id IS NULL AND requested_budget IS NULL)
    ) IS TRUE),
    CONSTRAINT chk_continuous_operation_result CHECK ((
        jsonb_typeof(result_json)='object' AND octet_length(result_json::text)<=8192
        AND result_json->>'paperRunId'=paper_run_id
        AND result_json->>'status' IN ('RUNNING','STALLED','STOPPED')
        AND (operation_type<>'PAUSE' OR result_json->>'status'='STOPPED')
        AND (operation_type NOT IN ('CREATE','RESUME') OR result_json->>'status'='RUNNING')
    ) IS TRUE),
    CONSTRAINT chk_continuous_operation_time CHECK (isfinite(completed_at))
);

CREATE INDEX idx_continuous_operations_owner_time
    ON continuous_sim_operations(owner_user_id,completed_at DESC,operation_id);
CREATE INDEX idx_continuous_operations_run_time
    ON continuous_sim_operations(paper_run_id,completed_at DESC,operation_id);

CREATE FUNCTION guard_continuous_sim_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'continuous operation result is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER trg_continuous_operation_immutable
    BEFORE UPDATE OR DELETE ON continuous_sim_operations
    FOR EACH ROW EXECUTE FUNCTION guard_continuous_sim_operation();

COMMENT ON TABLE continuous_sim_operations IS '连续SIM命令与原结果的不可变回执；业务修改和回执同事务提交，缺失回执不能被客户端推断为未执行';
COMMENT ON COLUMN continuous_sim_operations.operation_id IS '客户端发送前持久保存的原操作UUID；并发及重发使用同一编号';
COMMENT ON COLUMN continuous_sim_operations.owner_user_id IS '原发起用户的规范ID；其他用户包含管理员不能查询该回执';
COMMENT ON COLUMN continuous_sim_operations.actor IS '原认证用户名，用于创建归属和审计，不接受客户端自报';
COMMENT ON COLUMN continuous_sim_operations.operation_type IS 'CREATE创建独立连续SIM，PAUSE暂停轮询，RESUME按原前提恢复；不自动撤单或结束父运行';
COMMENT ON COLUMN continuous_sim_operations.requested_publish_id IS '仅CREATE保存原发布身份；回执重放不重新创建运行';
COMMENT ON COLUMN continuous_sim_operations.requested_budget IS '仅CREATE保存原独立SIM预算，最多八位小数，沿用正式预算范围';
COMMENT ON COLUMN continuous_sim_operations.paper_run_id IS '原命令作用的连续SIM运行，关联原持久游标而非另造运行状态';
COMMENT ON COLUMN continuous_sim_operations.input_sha256 IS '主体、动作、原发布或运行及规范十进制预算的长度前缀SHA256';
COMMENT ON COLUMN continuous_sim_operations.result_json IS '原命令完成时的状态快照；后续轮询或暂停恢复不改写此结果，当前状态另查';
COMMENT ON COLUMN continuous_sim_operations.trace_id IS '原命令追踪身份，与同事务审计对应';
COMMENT ON COLUMN continuous_sim_operations.completed_at IS '原业务修改与结果共同提交的完成时刻；不代表交易成交';
COMMENT ON FUNCTION guard_continuous_sim_operation() IS '原结果只允许插入，不允许更新或删除，避免把当前运行状态覆盖为原命令结果';
