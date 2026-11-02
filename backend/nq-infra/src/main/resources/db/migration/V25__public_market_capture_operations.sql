-- 只记录公开捕获请求及其原结果；行情与规则事实继续存于原数据集和public_market_captures。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';

CREATE TABLE public_market_capture_operations (
    capture_id uuid PRIMARY KEY CHECK(capture_id<>'00000000-0000-0000-0000-000000000000'::uuid),
    owner_user_id bigint NOT NULL REFERENCES users(id) ON DELETE RESTRICT CHECK(owner_user_id>0),
    actor varchar(128) NOT NULL CHECK(btrim(actor)<>''),
    requested_start timestamptz NOT NULL,
    requested_end timestamptz NOT NULL,
    state varchar(16) NOT NULL CHECK(state IN ('PENDING','SUCCEEDED','FAILED')),
    result_dataset_id uuid UNIQUE REFERENCES public_market_captures(dataset_id) ON DELETE RESTRICT,
    result_json jsonb,
    failure_code varchar(80),
    created_at timestamptz NOT NULL CHECK(isfinite(created_at)),
    updated_at timestamptz NOT NULL CHECK(isfinite(updated_at)),
    completed_at timestamptz,
    CONSTRAINT chk_public_capture_operation_window CHECK(
        isfinite(requested_start) AND isfinite(requested_end)
        AND requested_end-requested_start BETWEEN interval '1 hour' AND interval '100 hours'
        AND mod(extract(epoch FROM requested_start),3600)=0
        AND mod(extract(epoch FROM requested_end),3600)=0),
    CONSTRAINT chk_public_capture_operation_result CHECK((
        (state='PENDING' AND result_dataset_id IS NULL AND result_json IS NULL AND failure_code IS NULL
            AND completed_at IS NULL AND updated_at=created_at)
        OR (state='SUCCEEDED' AND result_dataset_id=capture_id AND result_json IS NOT NULL
            AND jsonb_typeof(result_json)='object' AND octet_length(result_json::text)<=32768
            AND result_json->>'datasetId'=capture_id::text AND failure_code IS NULL
            AND completed_at IS NOT NULL AND isfinite(completed_at) AND updated_at=completed_at)
        OR (state='FAILED' AND result_dataset_id IS NULL AND result_json IS NULL
            AND failure_code ~ '^PUBLIC_[A-Z0-9_]+$'
            AND completed_at IS NOT NULL AND isfinite(completed_at) AND updated_at=completed_at)
    ) IS TRUE)
);

CREATE FUNCTION preserve_public_capture_operation() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_capture public_market_captures%ROWTYPE; v_actor text;
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'public capture operation is retained' USING ERRCODE='23514';
    END IF;
    IF TG_OP='INSERT' THEN
        SELECT username INTO v_actor FROM users WHERE id=NEW.owner_user_id;
        IF v_actor IS DISTINCT FROM NEW.actor OR NEW.state<>'PENDING' THEN
            RAISE EXCEPTION 'public capture must start with authenticated identity and pending state' USING ERRCODE='23514';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.state<>'PENDING'
       OR ROW(NEW.capture_id,NEW.owner_user_id,NEW.actor,NEW.requested_start,NEW.requested_end,NEW.created_at)
          IS DISTINCT FROM ROW(OLD.capture_id,OLD.owner_user_id,OLD.actor,OLD.requested_start,OLD.requested_end,OLD.created_at)
       OR NEW.state NOT IN ('SUCCEEDED','FAILED') THEN
        RAISE EXCEPTION 'original public capture identity or completed result is immutable' USING ERRCODE='23514';
    END IF;
    IF NEW.state='SUCCEEDED' THEN
        SELECT * INTO v_capture FROM public_market_captures WHERE dataset_id=NEW.capture_id FOR SHARE;
        IF NOT FOUND THEN RAISE EXCEPTION 'canonical public capture missing' USING ERRCODE='23503'; END IF;
        IF v_capture.requested_start IS DISTINCT FROM NEW.requested_start
           OR v_capture.requested_end IS DISTINCT FROM NEW.requested_end
           OR NEW.result_json->>'venue' IS DISTINCT FROM 'OKX'
           OR NEW.result_json->>'marketType' IS DISTINCT FROM 'SPOT'
           OR NEW.result_json->>'instrument' IS DISTINCT FROM 'BTC-USDT'
           OR NEW.result_json->>'interval' IS DISTINCT FROM '1h'
           OR (NEW.result_json->>'start')::timestamptz IS DISTINCT FROM v_capture.requested_start
           OR (NEW.result_json->>'end')::timestamptz IS DISTINCT FROM v_capture.requested_end
           OR (NEW.result_json->>'barCount')::integer IS DISTINCT FROM v_capture.bar_count
           OR (NEW.result_json->>'observedAt')::timestamptz IS DISTINCT FROM v_capture.observed_at
           OR NEW.result_json->>'rawSha256' IS DISTINCT FROM v_capture.raw_sha256
           OR NEW.result_json->>'normalizedSha256' IS DISTINCT FROM v_capture.normalized_sha256
           OR NEW.result_json->>'consumedSha256' IS DISTINCT FROM v_capture.consumed_sha256
           OR NEW.result_json->>'replayVisibilityVersion' IS DISTINCT FROM v_capture.replay_visibility_version
           OR NEW.result_json->>'replayVisibilitySource' IS DISTINCT FROM v_capture.replay_visibility_source
           OR (NEW.result_json->>'ruleObservedAt')::timestamptz IS DISTINCT FROM v_capture.rule_observed_at
           OR NEW.result_json->>'ruleRawSha256' IS DISTINCT FROM v_capture.rule_raw_sha256
           OR NEW.result_json->>'ruleSha256' IS DISTINCT FROM v_capture.rule_sha256
           OR NEW.result_json->>'rulePolicy' IS DISTINCT FROM 'CURRENTLY_OBSERVED_PUBLIC_RULES'
           OR NOT EXISTS(SELECT 1 FROM marketdata_datasets WHERE dataset_id=NEW.capture_id
                         AND created_by=NEW.actor AND source='OKX_PUBLIC_CAPTURE') THEN
            RAISE EXCEPTION 'original result does not match canonical public capture' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_public_capture_operation_preserve BEFORE INSERT OR UPDATE OR DELETE
    ON public_market_capture_operations FOR EACH ROW EXECUTE FUNCTION preserve_public_capture_operation();

COMMENT ON TABLE public_market_capture_operations IS '公开捕获的稳定请求回执；PENDING不代表成功，提交结果与原捕获事实原子关联';
COMMENT ON COLUMN public_market_capture_operations.capture_id IS '客户端发送前保存的原UUID；同时作为成功数据集及捕获的身份';
COMMENT ON COLUMN public_market_capture_operations.owner_user_id IS '服务端认证用户ID，防止用户名被重新创建后读取另一用户的原结果';
COMMENT ON COLUMN public_market_capture_operations.actor IS '原请求认证用户名，冻结为该次数据集的创建者';
COMMENT ON COLUMN public_market_capture_operations.requested_start IS '原UTC整小时开始，属于稳定输入，不接受重放时更换';
COMMENT ON COLUMN public_market_capture_operations.requested_end IS '原UTC整小时结束，采用捕获原有半开窗口，最多100小时';
COMMENT ON COLUMN public_market_capture_operations.state IS 'PENDING可按原编号明确继续；SUCCEEDED和FAILED是不可变的原结果';
COMMENT ON COLUMN public_market_capture_operations.result_dataset_id IS '成功时关联既有public_market_captures，不建立第二套行情或规则事实';
COMMENT ON COLUMN public_market_capture_operations.result_json IS '成功原结果快照，完成时由数据库与正式捕获事实逐项核对';
COMMENT ON COLUMN public_market_capture_operations.failure_code IS '已确认来源失败的有界机器码，不保存异常原文、凭据或响应正文';
COMMENT ON COLUMN public_market_capture_operations.created_at IS '外部读取之前已提交的请求登记时刻';
COMMENT ON COLUMN public_market_capture_operations.updated_at IS '登记或终态提交时刻，使用持久化微秒精度';
COMMENT ON COLUMN public_market_capture_operations.completed_at IS '确认成功或失败的时刻；中断和回滚继续保留PENDING';
COMMENT ON FUNCTION preserve_public_capture_operation() IS '保留请求身份和终态，成功回执必须与原数据集、捕获窗口和来源摘要一致';
