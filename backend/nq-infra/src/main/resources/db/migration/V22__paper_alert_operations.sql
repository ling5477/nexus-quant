-- 告警操作沿用原告警事实；只新增原编号/回执，不新增交易或通知事实源。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

ALTER TABLE paper_run_alerts ADD CONSTRAINT uq_paper_run_alert_parent UNIQUE(paper_run_id,alert_id);

CREATE TABLE paper_alert_operations (
    operation_id uuid PRIMARY KEY,
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    actor varchar(128) NOT NULL,
    paper_run_id varchar(128) NOT NULL,
    operation_type varchar(16) NOT NULL CHECK(operation_type IN ('CREATE','ACK','RESOLVE')),
    alert_id varchar(128) NOT NULL,
    input_sha256 char(64) NOT NULL,
    trace_id varchar(128) NOT NULL,
    result_json jsonb NOT NULL,
    completed_at timestamptz NOT NULL,
    CONSTRAINT fk_paper_alert_operation_parent FOREIGN KEY(paper_run_id,alert_id)
        REFERENCES paper_run_alerts(paper_run_id,alert_id) ON DELETE RESTRICT,
    CONSTRAINT chk_paper_alert_operation_identity CHECK (
        btrim(actor)<>'' AND btrim(trace_id)<>'' AND input_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT chk_paper_alert_operation_result CHECK ((
        jsonb_typeof(result_json)='object' AND octet_length(result_json::text)<=65536
        AND result_json->>'paperRunId'=paper_run_id AND result_json->>'alertId'=alert_id
        AND ((operation_type='CREATE' AND result_json->>'status'='OPEN')
            OR (operation_type='ACK' AND result_json->>'status'='ACKED')
            OR (operation_type='RESOLVE' AND result_json->>'status'='RESOLVED'))
    ) IS TRUE),
    CONSTRAINT chk_paper_alert_operation_time CHECK (isfinite(completed_at))
);

CREATE INDEX idx_paper_alert_operations_owner_time
    ON paper_alert_operations(owner_user_id,completed_at DESC,operation_id);
CREATE INDEX idx_paper_alert_operations_run_time
    ON paper_alert_operations(paper_run_id,completed_at DESC,operation_id);

CREATE FUNCTION guard_paper_alert_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'paper alert operation result is immutable' USING ERRCODE='23514';
END;
$$;
CREATE TRIGGER trg_paper_alert_operation_immutable BEFORE UPDATE OR DELETE ON paper_alert_operations
    FOR EACH ROW EXECUTE FUNCTION guard_paper_alert_operation();

COMMENT ON TABLE paper_alert_operations IS '告警创建、确认和解决的原编号与不可变回执；原告警变更、回执和完成审计在同一事务提交';
COMMENT ON COLUMN paper_alert_operations.operation_id IS '客户端发送前保存的原操作UUID；恢复只查询或显式继续同一编号';
COMMENT ON COLUMN paper_alert_operations.owner_user_id IS '原认证主体的规范用户ID；包括其他管理员均不能查询此主体的原回执';
COMMENT ON COLUMN paper_alert_operations.actor IS '认证用户名，确认记录不得信任客户端自报操作者';
COMMENT ON COLUMN paper_alert_operations.paper_run_id IS '原请求父运行，复合外键保证原结果仍属于同一告警及运行';
COMMENT ON COLUMN paper_alert_operations.operation_type IS 'CREATE创建原告警、ACK确认、RESOLVE解决；阅读通知不产生这些操作';
COMMENT ON COLUMN paper_alert_operations.alert_id IS '原正式告警身份，不是单独的通知或交易记录';
COMMENT ON COLUMN paper_alert_operations.input_sha256 IS '主体、父运行、动作和完整输入的长度前缀SHA256；字段边界与空值均参与身份';
COMMENT ON COLUMN paper_alert_operations.trace_id IS '与同事务完成审计关联的原追踪身份';
COMMENT ON COLUMN paper_alert_operations.result_json IS '原操作完成时告警快照；后续确认或解决不得改写原结果';
COMMENT ON COLUMN paper_alert_operations.completed_at IS '本地原业务事务的完成时刻，不代表真实交易或通知已读';
COMMENT ON FUNCTION guard_paper_alert_operation() IS '拒绝更新或删除原回执，避免把当前状态伪装成原命令结果';
