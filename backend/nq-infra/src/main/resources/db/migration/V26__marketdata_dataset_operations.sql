-- 只增加原请求回执；数据集与覆盖历史继续由原表和正式业务服务拥有。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE marketdata_dataset_operations (
    operation_id uuid PRIMARY KEY CHECK(operation_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT CHECK(owner_user_id>0),
    actor varchar(128) NOT NULL CHECK(btrim(actor)<>''),
    kind varchar(16) NOT NULL CHECK(kind IN ('CREATE','REFRESH')),
    dataset_id uuid NOT NULL CHECK(dataset_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    request_json jsonb NOT NULL CHECK(jsonb_typeof(request_json)='object' AND octet_length(request_json::text)<=8192),
    state varchar(16) NOT NULL CHECK(state IN ('PENDING','SUCCEEDED','FAILED')),
    result_dataset_id uuid REFERENCES marketdata_datasets(dataset_id) ON DELETE RESTRICT,
    result_coverage_id uuid UNIQUE REFERENCES marketdata_dataset_coverage(coverage_id) ON DELETE RESTRICT,
    result_json jsonb,
    failure_code varchar(80),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at)),
    completed_at timestamptz,
    CONSTRAINT chk_dataset_operation_creation_identity CHECK(kind<>'CREATE' OR dataset_id=operation_id),
    CONSTRAINT chk_dataset_operation_result CHECK((
        (state='PENDING' AND result_dataset_id IS NULL AND result_coverage_id IS NULL AND result_json IS NULL
            AND failure_code IS NULL AND completed_at IS NULL AND updated_at=created_at)
        OR (state='SUCCEEDED' AND result_dataset_id=dataset_id AND result_coverage_id=operation_id
            AND result_json IS NOT NULL AND jsonb_typeof(result_json)='object' AND octet_length(result_json::text)<=32768
            AND failure_code IS NULL AND completed_at IS NOT NULL AND isfinite(completed_at) AND updated_at=completed_at)
        OR (state='FAILED' AND result_dataset_id IS NULL AND result_coverage_id IS NULL AND result_json IS NULL
            AND failure_code IN ('DATASET_ID_OCCUPIED','DATASET_NOT_FOUND','PUBLIC_CAPTURE_DATASET_IMMUTABLE')
            AND completed_at IS NOT NULL AND isfinite(completed_at) AND updated_at=completed_at)
    ) IS TRUE)
);
CREATE INDEX idx_dataset_operations_result_dataset ON marketdata_dataset_operations(result_dataset_id)
    WHERE result_dataset_id IS NOT NULL;

CREATE FUNCTION preserve_marketdata_dataset_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_actor text;
    v_key text;
    v_dataset marketdata_datasets%ROWTYPE;
    v_coverage marketdata_dataset_coverage%ROWTYPE;
    v_result jsonb;
    v_quality jsonb;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'dataset original operation is retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        SELECT username INTO v_actor FROM users WHERE id=NEW.owner_user_id;
        IF v_actor IS DISTINCT FROM NEW.actor OR NEW.state<>'PENDING' THEN
            RAISE EXCEPTION 'dataset operation requires original authenticated identity and pending state' USING ERRCODE='23514';
        END IF;
        IF NEW.kind='REFRESH' THEN
            IF NEW.request_json<>'{}'::jsonb THEN
                RAISE EXCEPTION 'refresh input is only the frozen dataset identity' USING ERRCODE='23514';
            END IF;
        ELSE
            IF NOT NEW.request_json ?& ARRAY['datasetName','exchangeCode','marketType','symbol','interval','startTime','endTime']
               OR NEW.request_json-ARRAY['datasetName','exchangeCode','marketType','symbol','interval','startTime','endTime']<>'{}'::jsonb THEN
                RAISE EXCEPTION 'incomplete original dataset input' USING ERRCODE='23514';
            END IF;
            FOREACH v_key IN ARRAY ARRAY['datasetName','exchangeCode','marketType','symbol','interval','startTime','endTime'] LOOP
                IF jsonb_typeof(NEW.request_json->v_key) IS DISTINCT FROM 'string' THEN
                    RAISE EXCEPTION 'dataset input fields must be text' USING ERRCODE='23514';
                END IF;
            END LOOP;
            IF (length(NEW.request_json->>'datasetName') BETWEEN 1 AND 255
                AND btrim(NEW.request_json->>'datasetName')=NEW.request_json->>'datasetName'
                AND NEW.request_json->>'datasetName' !~ '[[:cntrl:]]'
                AND NEW.request_json->>'exchangeCode' IN ('OKX','BINANCE')
                AND NEW.request_json->>'marketType'='SPOT'
                AND NEW.request_json->>'symbol' IN ('BTC-USDT','ETH-USDT','SOL-USDT','BNB-USDT')
                AND NEW.request_json->>'interval' IN ('1m','5m','15m','1h','4h','1d')
                AND NEW.request_json->>'startTime' ~ 'Z$' AND NEW.request_json->>'endTime' ~ 'Z$'
                AND NEW.request_json->>'startTime' !~ '\.[0-9]{7}' AND NEW.request_json->>'endTime' !~ '\.[0-9]{7}'
                AND isfinite((NEW.request_json->>'startTime')::timestamptz)
                AND isfinite((NEW.request_json->>'endTime')::timestamptz)
                AND (NEW.request_json->>'endTime')::timestamptz>(NEW.request_json->>'startTime')::timestamptz
            ) IS NOT TRUE THEN
                RAISE EXCEPTION 'invalid canonical dataset input' USING ERRCODE='23514';
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.state<>'PENDING'
       OR ROW(NEW.operation_id,NEW.owner_user_id,NEW.actor,NEW.kind,NEW.dataset_id,NEW.request_json,NEW.created_at)
          IS DISTINCT FROM ROW(OLD.operation_id,OLD.owner_user_id,OLD.actor,OLD.kind,OLD.dataset_id,OLD.request_json,OLD.created_at)
       OR NEW.state NOT IN ('SUCCEEDED','FAILED') THEN
        RAISE EXCEPTION 'dataset original identity or terminal receipt is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.state='SUCCEEDED' THEN
        SELECT * INTO v_dataset FROM marketdata_datasets WHERE dataset_id=NEW.dataset_id FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'canonical dataset missing' USING ERRCODE='23503'; END IF;
        SELECT * INTO v_coverage FROM marketdata_dataset_coverage WHERE coverage_id=NEW.operation_id FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'original coverage missing' USING ERRCODE='23503'; END IF;
        v_result=NEW.result_json->'dataset'; v_quality=NEW.result_json->'coverage';
        IF v_dataset.source<>'marketdata_bars'
           OR v_coverage.dataset_id IS DISTINCT FROM NEW.dataset_id
           OR v_coverage.created_at IS DISTINCT FROM v_dataset.updated_at
           OR v_coverage.actual_bars IS DISTINCT FROM v_dataset.bar_count
           OR v_coverage.missing_bars IS DISTINCT FROM v_dataset.gap_count
           OR v_coverage.quality_status IS DISTINCT FROM v_dataset.quality_status
           OR (NEW.kind='CREATE' AND (v_dataset.created_by IS DISTINCT FROM NEW.actor OR v_dataset.request_json IS DISTINCT FROM NEW.request_json))
           OR ROW((v_result->>'datasetId')::uuid,v_result->>'datasetName',v_result->>'exchangeCode',v_result->>'marketType',
                  v_result->>'symbol',v_result->>'interval',(v_result->>'startTime')::timestamptz,(v_result->>'endTime')::timestamptz,
                  v_result->>'status',v_result->>'qualityStatus',(v_result->>'barCount')::bigint,(v_result->>'gapCount')::bigint,
                  v_result->>'source',v_result->>'createdBy',(v_result->>'createdAt')::timestamptz,(v_result->>'updatedAt')::timestamptz,
                  (v_result->>'requestJson')::jsonb)
              IS DISTINCT FROM ROW(v_dataset.dataset_id,v_dataset.dataset_name,v_dataset.exchange_code,v_dataset.market_type,
                  v_dataset.symbol,v_dataset."interval",v_dataset.start_time,v_dataset.end_time,v_dataset.status,v_dataset.quality_status,
                  v_dataset.bar_count,v_dataset.gap_count,v_dataset.source,v_dataset.created_by,v_dataset.created_at,v_dataset.updated_at,v_dataset.request_json)
           OR ROW((v_quality->>'coverageId')::uuid,(v_quality->>'datasetId')::uuid,
                  (v_quality->>'rangeStartTime')::timestamptz,(v_quality->>'rangeEndTime')::timestamptz,
                  (v_quality->>'expectedBars')::bigint,(v_quality->>'actualBars')::bigint,(v_quality->>'missingBars')::bigint,
                  (v_quality->>'duplicateBars')::bigint,(v_quality->>'invalidBars')::bigint,v_quality->>'qualityStatus',
                  (v_quality->>'summaryJson')::jsonb,(v_quality->>'createdAt')::timestamptz)
              IS DISTINCT FROM ROW(v_coverage.coverage_id,v_coverage.dataset_id,v_coverage.range_start_time,v_coverage.range_end_time,
                  v_coverage.expected_bars,v_coverage.actual_bars,v_coverage.missing_bars,v_coverage.duplicate_bars,v_coverage.invalid_bars,
                  v_coverage.quality_status,v_coverage.summary_json,v_coverage.created_at) THEN
            RAISE EXCEPTION 'original receipt does not match dataset and original coverage' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_marketdata_dataset_operation_preserve BEFORE INSERT OR UPDATE OR DELETE
    ON marketdata_dataset_operations FOR EACH ROW EXECUTE FUNCTION preserve_marketdata_dataset_operation();

COMMENT ON TABLE marketdata_dataset_operations IS '数据集创建与质量刷新的稳定原请求和原次回执；不代替数据集或覆盖历史';
COMMENT ON COLUMN marketdata_dataset_operations.operation_id IS '客户端提交前保存的非零UUID；成功覆盖记录使用同号，创建时也作为数据集编号';
COMMENT ON COLUMN marketdata_dataset_operations.owner_user_id IS '当前认证用户ID，原结果仅该用户可读，不能按可重建用户名转移所有权';
COMMENT ON COLUMN marketdata_dataset_operations.actor IS '原请求用户名；用于冻结创建者与审计，恢复时不更换';
COMMENT ON COLUMN marketdata_dataset_operations.kind IS 'CREATE固定新数据集输入；REFRESH只指向原数据集并生成本次覆盖';
COMMENT ON COLUMN marketdata_dataset_operations.dataset_id IS '原业务对象编号；登记时允许尚不存在，成功时必须关联正式数据集';
COMMENT ON COLUMN marketdata_dataset_operations.request_json IS '经过正式服务校验的规范创建输入，或刷新空对象；重放不得修改';
COMMENT ON COLUMN marketdata_dataset_operations.state IS '登记PENDING可明确继续，未知SQL失败回滚后保持；终态永久重放';
COMMENT ON COLUMN marketdata_dataset_operations.result_dataset_id IS '成功时引用现有数据集；以后质量变化不覆盖此回执';
COMMENT ON COLUMN marketdata_dataset_operations.result_coverage_id IS '与操作编号相同的原次覆盖，不能绑定别次刷新历史';
COMMENT ON COLUMN marketdata_dataset_operations.result_json IS '完成时与数据集和原次覆盖逐字段核对的结果快照';
COMMENT ON COLUMN marketdata_dataset_operations.failure_code IS '仅保存确定的缺失、身份占用或不可变来源拒绝；未知技术错误不伪装终态';
COMMENT ON COLUMN marketdata_dataset_operations.created_at IS '业务写入前独立提交登记的微秒时刻';
COMMENT ON COLUMN marketdata_dataset_operations.updated_at IS '登记或终态时刻；PENDING不更新以免篡改请求';
COMMENT ON COLUMN marketdata_dataset_operations.completed_at IS '确认终态时刻；数据集、覆盖、主状态与成功回执同事务提交';
COMMENT ON INDEX idx_dataset_operations_result_dataset IS '保持正式数据集外键检查有界，不添加无消费者的历史列表索引';
COMMENT ON FUNCTION preserve_marketdata_dataset_operation() IS '保留原身份与终态；原回执必须对应同号覆盖及正式数据集，不能伪造成功';
