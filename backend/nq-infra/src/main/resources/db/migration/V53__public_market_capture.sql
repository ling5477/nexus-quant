-- 公开行情捕获是不可变实验输入；旧 marketdata_bars 的摄取时间与可见时间语义保持原样。
SET lock_timeout = '5s';

CREATE TABLE public_market_captures (
    dataset_id UUID PRIMARY KEY REFERENCES marketdata_datasets(dataset_id) ON DELETE RESTRICT,
    observed_at TIMESTAMPTZ NOT NULL,
    requested_start TIMESTAMPTZ NOT NULL,
    requested_end TIMESTAMPTZ NOT NULL,
    request_path TEXT NOT NULL,
    raw_response TEXT NOT NULL,
    raw_sha256 CHAR(64) NOT NULL,
    normalized_sha256 CHAR(64) NOT NULL,
    consumed_sha256 CHAR(64) NOT NULL,
    replay_visibility_version VARCHAR(64) NOT NULL,
    replay_visibility_source VARCHAR(128) NOT NULL,
    rule_observed_at TIMESTAMPTZ NOT NULL,
    rule_sha256 CHAR(64) NOT NULL,
    rule_json JSONB NOT NULL,
    bars_json JSONB NOT NULL,
    bar_count INTEGER NOT NULL CHECK (bar_count BETWEEN 1 AND 500),
    first_open_time TIMESTAMPTZ NOT NULL,
    last_open_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_public_market_capture_window CHECK (requested_start < requested_end),
    CONSTRAINT chk_public_market_capture_range CHECK (
        first_open_time >= requested_start AND last_open_time < requested_end
        AND last_open_time >= first_open_time
    ),
    CONSTRAINT chk_public_market_capture_replay_source CHECK (
        replay_visibility_source = 'EXPERIMENT_ASSUMPTION'
    )
);

CREATE OR REPLACE FUNCTION reject_public_market_capture_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'public market capture is immutable';
END;
$$;

CREATE TRIGGER trg_public_market_capture_immutable
BEFORE UPDATE OR DELETE ON public_market_captures
FOR EACH ROW EXECUTE FUNCTION reject_public_market_capture_mutation();

CREATE OR REPLACE FUNCTION reject_public_capture_dataset_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.source = 'OKX_PUBLIC_CAPTURE' THEN
        RAISE EXCEPTION 'public capture dataset is immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_public_capture_dataset_immutable
BEFORE UPDATE OR DELETE ON marketdata_datasets
FOR EACH ROW EXECUTE FUNCTION reject_public_capture_dataset_mutation();

COMMENT ON TABLE public_market_captures IS
    '不可变公开行情响应及规范化回放输入；observed_at 是实际观察事实，bars_json.availableAt 是版本化实验可见时间假设。';
