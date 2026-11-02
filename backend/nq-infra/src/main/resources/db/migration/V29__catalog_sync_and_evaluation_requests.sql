-- 原请求回执不执行异步任务；业务目录与评估仍由已有正式服务和表拥有。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE instrument_catalog_sync_requests (
    request_id UUID PRIMARY KEY CHECK(request_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT CHECK(owner_user_id>0),
    exchange_scope VARCHAR(64) NOT NULL CHECK(exchange_scope IN ('ALL','OKX','BINANCE')),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK(isfinite(created_at)),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_instrument_catalog_sync_requests_result CHECK ((
       (state='PENDING' AND result_json IS NULL AND completed_at IS NULL)
       OR ((state='SUCCEEDED' AND result_json IS NOT NULL AND result_json->'exchangeCodes' = CASE exchange_scope WHEN 'ALL' THEN '["OKX","BINANCE"]'::jsonb ELSE jsonb_build_array(exchange_scope) END) AND jsonb_typeof(result_json)='object'
           AND octet_length(result_json::text)<=32768 AND completed_at IS NOT NULL
           AND isfinite(completed_at) AND completed_at>=created_at)
    ) IS TRUE)
);
CREATE INDEX idx_instrument_catalog_sync_requests_owner ON instrument_catalog_sync_requests(owner_user_id,request_id);
COMMENT ON TABLE instrument_catalog_sync_requests IS '稳定原请求及不可变响应回执；PENDING不能解释为失败或重新执行许可';
COMMENT ON COLUMN instrument_catalog_sync_requests.request_id IS '客户端首次发送前持久保存的UUID；重放不得更换';
COMMENT ON COLUMN instrument_catalog_sync_requests.owner_user_id IS '原认证主体，查询和重放均需匹配';
COMMENT ON COLUMN instrument_catalog_sync_requests.exchange_scope IS '冻结的原业务范围，重放不得替换';
COMMENT ON COLUMN instrument_catalog_sync_requests.state IS 'PENDING为结果待确认；终态只与正式业务结果同事务写入';
COMMENT ON COLUMN instrument_catalog_sync_requests.result_json IS '正式服务原次返回的响应快照，只作回执，不作为新的业务计算或账务来源';
COMMENT ON COLUMN instrument_catalog_sync_requests.created_at IS '原请求登记时间';
COMMENT ON COLUMN instrument_catalog_sync_requests.completed_at IS '业务事实和回执完成的同事务时间';
CREATE FUNCTION preserve_instrument_catalog_sync_requests() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'original request cannot be deleted' USING ERRCODE='23514'; END IF;
    IF (NEW.request_id,NEW.owner_user_id,NEW.exchange_scope,NEW.created_at)
       IS DISTINCT FROM (OLD.request_id,OLD.owner_user_id,OLD.exchange_scope,OLD.created_at)
       OR OLD.state<>'PENDING' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'original request identity and terminal receipt are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_preserve_instrument_catalog_sync_requests BEFORE UPDATE OR DELETE ON instrument_catalog_sync_requests
FOR EACH ROW EXECUTE FUNCTION preserve_instrument_catalog_sync_requests();

CREATE TABLE backtest_evaluation_requests (
    request_id UUID PRIMARY KEY CHECK(request_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT CHECK(owner_user_id>0),
    backtest_run_id VARCHAR(128) NOT NULL REFERENCES backtest_runs(backtest_run_id) ON DELETE RESTRICT CHECK(length(backtest_run_id) BETWEEN 1 AND 128),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK(isfinite(created_at)),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chk_backtest_evaluation_requests_result CHECK ((
       (state='PENDING' AND result_json IS NULL AND completed_at IS NULL)
       OR ((state IN ('SUCCEEDED','FAILED') AND result_json IS NOT NULL AND result_json->>'backtestRunId'=backtest_run_id AND length(result_json->>'evalReportId')>0 AND result_json->>'evaluationStatus'=state) AND jsonb_typeof(result_json)='object'
           AND octet_length(result_json::text)<=2097152 AND completed_at IS NOT NULL
           AND isfinite(completed_at) AND completed_at>=created_at)
    ) IS TRUE)
);
CREATE INDEX idx_backtest_evaluation_requests_owner ON backtest_evaluation_requests(owner_user_id,request_id);
COMMENT ON TABLE backtest_evaluation_requests IS '稳定原请求及不可变响应回执；PENDING不能解释为失败或重新执行许可';
COMMENT ON COLUMN backtest_evaluation_requests.request_id IS '客户端首次发送前持久保存的UUID；重放不得更换';
COMMENT ON COLUMN backtest_evaluation_requests.owner_user_id IS '原认证主体，查询和重放均需匹配';
COMMENT ON COLUMN backtest_evaluation_requests.backtest_run_id IS '冻结的原业务范围，重放不得替换';
COMMENT ON COLUMN backtest_evaluation_requests.state IS 'PENDING为结果待确认；终态只与正式业务结果同事务写入';
COMMENT ON COLUMN backtest_evaluation_requests.result_json IS '正式服务原次返回的响应快照，只作回执，不作为新的业务计算或账务来源';
COMMENT ON COLUMN backtest_evaluation_requests.created_at IS '原请求登记时间';
COMMENT ON COLUMN backtest_evaluation_requests.completed_at IS '业务事实和回执完成的同事务时间';
CREATE FUNCTION preserve_backtest_evaluation_requests() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'original request cannot be deleted' USING ERRCODE='23514'; END IF;
    IF (NEW.request_id,NEW.owner_user_id,NEW.backtest_run_id,NEW.created_at)
       IS DISTINCT FROM (OLD.request_id,OLD.owner_user_id,OLD.backtest_run_id,OLD.created_at)
       OR OLD.state<>'PENDING' AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION 'original request identity and terminal receipt are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_preserve_backtest_evaluation_requests BEFORE UPDATE OR DELETE ON backtest_evaluation_requests
FOR EACH ROW EXECUTE FUNCTION preserve_backtest_evaluation_requests();
