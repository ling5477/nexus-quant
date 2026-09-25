-- 策略 SIM：只为新隔离 SIM run 增加 canonical account 关联和策略决策血缘。
-- 历史 Paper run 保持 NULL，旧 paper_trading_orders/trades/positions 只读兼容。
SET lock_timeout = '5s';

ALTER TABLE marketdata_bars ADD COLUMN available_at TIMESTAMPTZ;
ALTER TABLE marketdata_bars ADD CONSTRAINT chk_marketdata_bar_available_at
    CHECK (available_at IS NULL OR available_at >= close_time);
COMMENT ON COLUMN marketdata_bars.available_at IS
    '来源事实可证明的 bar 可见时间；旧行 NULL 时读取侧仅以入库时间保守解释';

ALTER TABLE paper_trading_runs
    ADD COLUMN canonical_account_id BIGINT REFERENCES accounts(account_id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_strategy_sim_paper_run_canonical_account
    ON paper_trading_runs(canonical_account_id) WHERE canonical_account_id IS NOT NULL;

CREATE TABLE strategy_sim_decisions (
    decision_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL REFERENCES paper_trading_runs(paper_run_id) ON DELETE RESTRICT,
    canonical_account_id BIGINT NOT NULL REFERENCES accounts(account_id) ON DELETE RESTRICT,
    strategy_version_id VARCHAR(128) NOT NULL REFERENCES strategy_versions(strategy_version_id) ON DELETE RESTRICT,
    strategy_checksum VARCHAR(64) NOT NULL,
    signal_open_time TIMESTAMPTZ NOT NULL,
    signal_available_at TIMESTAMPTZ NOT NULL,
    execution_open_time TIMESTAMPTZ,
    input_sha256 VARCHAR(64) NOT NULL,
    execution_bar_sha256 VARCHAR(64),
    input_snapshot_json JSONB NOT NULL,
    target_exposure NUMERIC(10, 8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    side VARCHAR(4),
    quantity NUMERIC(38, 8),
    execution_price NUMERIC(38, 8),
    fee_rate NUMERIC(12, 8) NOT NULL,
    slippage_bps NUMERIC(12, 4) NOT NULL,
    strategy_run_id VARCHAR(64) UNIQUE REFERENCES strategy_runs(strategy_run_id) ON DELETE RESTRICT,
    order_id VARCHAR(64) UNIQUE REFERENCES orders(order_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_strategy_sim_decision_window UNIQUE(paper_run_id, signal_open_time),
    CONSTRAINT chk_strategy_sim_decision_identity CHECK (
        strategy_checksum ~ '^[0-9a-f]{64}$' AND input_sha256 ~ '^[0-9a-f]{64}$'
        AND (execution_bar_sha256 IS NULL OR execution_bar_sha256 ~ '^[0-9a-f]{64}$')),
    CONSTRAINT chk_strategy_sim_decision_time CHECK (
        signal_available_at >= signal_open_time
        AND (execution_open_time IS NULL OR execution_open_time > signal_open_time)
        AND (status <> 'ACCEPTED' OR (execution_open_time > signal_available_at
            AND execution_bar_sha256 IS NOT NULL))),
    CONSTRAINT chk_strategy_sim_execution_event CHECK (
        (execution_open_time IS NULL AND execution_bar_sha256 IS NULL AND execution_price IS NULL)
        OR (execution_open_time IS NOT NULL AND execution_bar_sha256 IS NOT NULL
            AND execution_price > 0)),
    CONSTRAINT chk_strategy_sim_decision_target CHECK (target_exposure BETWEEN 0 AND 1),
    CONSTRAINT chk_strategy_sim_decision_status CHECK (
        status IN ('NO_SIGNAL','NOT_TRADABLE','RISK_REJECTED','ACCEPTED')),
    CONSTRAINT chk_strategy_sim_decision_order CHECK (
        (status = 'ACCEPTED' AND strategy_run_id IS NOT NULL AND order_id IS NOT NULL
            AND side IN ('BUY','SELL') AND quantity > 0 AND execution_price > 0)
        OR (status = 'RISK_REJECTED' AND
            ((strategy_run_id IS NULL AND order_id IS NULL)
             OR (strategy_run_id IS NOT NULL AND order_id IS NOT NULL)))
        OR (status IN ('NO_SIGNAL','NOT_TRADABLE')
            AND strategy_run_id IS NULL AND order_id IS NULL)),
    CONSTRAINT chk_strategy_sim_decision_cost CHECK (fee_rate >= 0 AND fee_rate < 1
        AND slippage_bps >= 0 AND slippage_bps < 10000)
);

CREATE INDEX idx_strategy_sim_decisions_run_time
    ON strategy_sim_decisions(paper_run_id, signal_open_time DESC);

CREATE UNIQUE INDEX uq_strategy_sim_decision_request
    ON strategy_sim_decisions(left(decision_id, 60));

COMMENT ON COLUMN paper_trading_runs.canonical_account_id IS
    '策略 SIM 新 SIM run 的隔离 canonical accounts 关联；历史研究侧 Paper run 保持 NULL';
COMMENT ON TABLE strategy_sim_decisions IS
    '策略 SIM 策略决策与既有 strategy_runs/orders 的关联及可重放输入；不是第二套订单或成交事实';
COMMENT ON COLUMN strategy_sim_decisions.input_snapshot_json IS
    '实际消费的 closed signal bars 与后续执行事件快照，按对应 SHA-256 校验；不得含凭证';
COMMENT ON COLUMN strategy_sim_decisions.reason IS
    '无信号、不可交易或风控拒绝原因；ACCEPTED 仅表示 canonical 订单已接受，不伪造成交';

CREATE FUNCTION strategy_sim_preserve_sim_account() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.canonical_account_id IS NOT NULL
       AND NEW.canonical_account_id IS DISTINCT FROM OLD.canonical_account_id THEN
        RAISE EXCEPTION 'Strategy SIM canonical account is immutable';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_strategy_sim_preserve_sim_account BEFORE UPDATE ON paper_trading_runs
    FOR EACH ROW EXECUTE FUNCTION strategy_sim_preserve_sim_account();

CREATE FUNCTION strategy_sim_preserve_decision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'strategy SIM decision is append only';
    END IF;
    IF OLD.status <> 'NOT_TRADABLE' OR OLD.reason <> 'DECIDING'
       OR NEW.status = 'NOT_TRADABLE' AND NEW.reason = 'DECIDING'
       OR ROW(NEW.decision_id,NEW.paper_run_id,NEW.canonical_account_id,
              NEW.strategy_version_id,NEW.strategy_checksum,NEW.signal_open_time,
              NEW.signal_available_at,NEW.execution_open_time,NEW.input_sha256,
              NEW.execution_bar_sha256,NEW.input_snapshot_json,NEW.target_exposure,
              NEW.side,NEW.quantity,NEW.execution_price,NEW.fee_rate,NEW.slippage_bps,
              NEW.created_at)
          IS DISTINCT FROM
          ROW(OLD.decision_id,OLD.paper_run_id,OLD.canonical_account_id,
              OLD.strategy_version_id,OLD.strategy_checksum,OLD.signal_open_time,
              OLD.signal_available_at,OLD.execution_open_time,OLD.input_sha256,
              OLD.execution_bar_sha256,OLD.input_snapshot_json,OLD.target_exposure,
              OLD.side,OLD.quantity,OLD.execution_price,OLD.fee_rate,OLD.slippage_bps,
              OLD.created_at) THEN
        RAISE EXCEPTION 'strategy SIM decision is immutable after completion';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_strategy_sim_preserve_decision BEFORE UPDATE OR DELETE ON strategy_sim_decisions
    FOR EACH ROW EXECUTE FUNCTION strategy_sim_preserve_decision();

-- 订单在 canonical 事务中检查 run 状态并持有 SHARE 锁；并发 stop 必须等已准入订单提交。
CREATE FUNCTION strategy_sim_guard_sim_order() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d strategy_sim_decisions%ROWTYPE; run_status text;
BEGIN
    IF NEW.client_order_id NOT LIKE 'coid-sim-%' THEN RETURN NEW; END IF;
    SELECT * INTO d FROM strategy_sim_decisions
    WHERE 'coid-sim-' || left(decision_id, 60) = NEW.client_order_id;
    IF NOT FOUND OR d.reason <> 'DECIDING' OR d.status <> 'NOT_TRADABLE'
       OR d.canonical_account_id <> NEW.account_id
       OR NEW.venue <> 'PAPER' OR NEW.trade_env <> 'SIM' THEN
        RAISE EXCEPTION 'strategy SIM decision binding invalid' USING ERRCODE='23514';
    END IF;
    SELECT status INTO run_status FROM paper_trading_runs
    WHERE paper_run_id=d.paper_run_id FOR SHARE;
    IF run_status <> 'RUNNING' THEN
        RAISE EXCEPTION 'strategy SIM run stopped' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_strategy_sim_guard_sim_order BEFORE INSERT ON orders
    FOR EACH ROW EXECUTE FUNCTION strategy_sim_guard_sim_order();
