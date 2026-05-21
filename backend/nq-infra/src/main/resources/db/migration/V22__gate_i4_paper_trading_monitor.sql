-- V22__gate_i4_paper_trading_monitor.sql
-- 目的：落实 GateI-4 Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘和异常停机最小数据模型。
-- Why:
-- 1) GateI-4 需要把 Paper run 的风控结果、资金/持仓曲线、交易复盘和异常停机事件持久化，形成虚拟币量化 V1 完整闭环；
-- 2) 本 migration 只新增 GateI-4 必要表，不修改历史 migration，不改交易核心状态机；
-- 3) JSONB 快照字段只保存可审计业务输入，禁止保存 token、cookie、密钥等敏感信息。

-- ============================================================
-- 1. paper_risk_check_results
-- ============================================================
CREATE TABLE paper_risk_check_results (
    risk_result_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    check_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    message VARCHAR(512),
    input_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_risk_results_status CHECK (status IN ('PASSED', 'REJECTED', 'WARNING')),
    CONSTRAINT chk_risk_results_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT fk_risk_results_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_risk_results_run_id ON paper_risk_check_results (paper_run_id, created_at DESC);

COMMENT ON TABLE paper_risk_check_results IS 'GateI-4 Paper Trading 风控检查结果表：记录 Paper run 的风控检查事实，第一版只做最小健康检查，不接复杂风控策略平台';
COMMENT ON COLUMN paper_risk_check_results.risk_result_id IS '风控结果主键，业务可读 ID，例如 rrc-<uuid>';
COMMENT ON COLUMN paper_risk_check_results.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN paper_risk_check_results.check_type IS '风控检查类型，第一版固定为 BASIC_HEALTH_CHECK；后续可扩展 POSITION_LIMIT、DRAWDOWN_LIMIT 等';
COMMENT ON COLUMN paper_risk_check_results.status IS '风控检查结果状态：PASSED 通过；REJECTED 拒绝；WARNING 警告';
COMMENT ON COLUMN paper_risk_check_results.severity IS '风控检查严重程度：LOW 低；MEDIUM 中；HIGH 高；CRITICAL 严重';
COMMENT ON COLUMN paper_risk_check_results.message IS '风控检查结果摘要消息，可空';
COMMENT ON COLUMN paper_risk_check_results.input_snapshot_json IS '风控检查输入快照 JSONB，保存检查时的 run/position/equity 摘要；不得保存敏感凭证';
COMMENT ON COLUMN paper_risk_check_results.result_snapshot_json IS '风控检查输出快照 JSONB，保存检查结果详情；不得保存敏感凭证';
COMMENT ON COLUMN paper_risk_check_results.created_at IS '风控检查执行时间，UTC';

-- ============================================================
-- 2. equity_curve_snapshots
-- ============================================================
CREATE TABLE equity_curve_snapshots (
    equity_snapshot_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL,
    total_equity NUMERIC(36, 18) NOT NULL,
    cash_balance NUMERIC(36, 18) NOT NULL,
    position_value NUMERIC(36, 18) NOT NULL,
    unrealized_pnl NUMERIC(36, 18) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(36, 18) NOT NULL DEFAULT 0,
    drawdown NUMERIC(36, 18) NOT NULL DEFAULT 0,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_equity_curve_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_equity_curve_run_time ON equity_curve_snapshots (paper_run_id, snapshot_time DESC);

COMMENT ON TABLE equity_curve_snapshots IS 'GateI-4 Paper Trading 资金曲线快照表：记录 Paper run 的权益时间序列，用于资金曲线展示和回撤计算';
COMMENT ON COLUMN equity_curve_snapshots.equity_snapshot_id IS '资金曲线快照主键，业务可读 ID，例如 eqs-<uuid>';
COMMENT ON COLUMN equity_curve_snapshots.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN equity_curve_snapshots.snapshot_time IS '快照时间点，UTC；按此字段排序形成资金曲线';
COMMENT ON COLUMN equity_curve_snapshots.total_equity IS '总权益 = cash_balance + position_value';
COMMENT ON COLUMN equity_curve_snapshots.cash_balance IS '现金余额';
COMMENT ON COLUMN equity_curve_snapshots.position_value IS '持仓市值';
COMMENT ON COLUMN equity_curve_snapshots.unrealized_pnl IS '未实现盈亏';
COMMENT ON COLUMN equity_curve_snapshots.realized_pnl IS '已实现盈亏累计';
COMMENT ON COLUMN equity_curve_snapshots.drawdown IS '当前回撤值，第一版口径为 (peak_equity - current_equity) / peak_equity';
COMMENT ON COLUMN equity_curve_snapshots.source IS '快照来源标识，例如 SYSTEM、MANUAL、RECONCILE';
COMMENT ON COLUMN equity_curve_snapshots.created_at IS '快照写入时间，UTC';

-- ============================================================
-- 3. position_curve_snapshots
-- ============================================================
CREATE TABLE position_curve_snapshots (
    position_snapshot_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    snapshot_time TIMESTAMPTZ NOT NULL,
    quantity NUMERIC(36, 18) NOT NULL,
    avg_price NUMERIC(36, 18) NOT NULL,
    mark_price NUMERIC(36, 18) NOT NULL,
    position_value NUMERIC(36, 18) NOT NULL,
    unrealized_pnl NUMERIC(36, 18) NOT NULL DEFAULT 0,
    realized_pnl NUMERIC(36, 18) NOT NULL DEFAULT 0,
    source VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_position_curve_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_position_curve_run_time ON position_curve_snapshots (paper_run_id, snapshot_time DESC);
CREATE INDEX idx_position_curve_run_symbol ON position_curve_snapshots (paper_run_id, symbol, snapshot_time DESC);

COMMENT ON TABLE position_curve_snapshots IS 'GateI-4 Paper Trading 持仓曲线快照表：记录 Paper run 按 symbol 的持仓时间序列，用于持仓曲线展示';
COMMENT ON COLUMN position_curve_snapshots.position_snapshot_id IS '持仓曲线快照主键，业务可读 ID，例如 pcs-<uuid>';
COMMENT ON COLUMN position_curve_snapshots.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN position_curve_snapshots.symbol IS '持仓交易对';
COMMENT ON COLUMN position_curve_snapshots.snapshot_time IS '快照时间点，UTC；按此字段排序形成持仓曲线';
COMMENT ON COLUMN position_curve_snapshots.quantity IS '持仓数量';
COMMENT ON COLUMN position_curve_snapshots.avg_price IS '持仓均价';
COMMENT ON COLUMN position_curve_snapshots.mark_price IS '标记价格（快照时刻市场价）';
COMMENT ON COLUMN position_curve_snapshots.position_value IS '持仓市值 = quantity * mark_price';
COMMENT ON COLUMN position_curve_snapshots.unrealized_pnl IS '未实现盈亏';
COMMENT ON COLUMN position_curve_snapshots.realized_pnl IS '已实现盈亏累计';
COMMENT ON COLUMN position_curve_snapshots.source IS '快照来源标识';
COMMENT ON COLUMN position_curve_snapshots.created_at IS '快照写入时间，UTC';

-- ============================================================
-- 4. trade_replay_records
-- ============================================================
CREATE TABLE trade_replay_records (
    replay_record_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    paper_order_id VARCHAR(64),
    paper_trade_id VARCHAR(64),
    replay_time TIMESTAMPTZ NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    side VARCHAR(8),
    price NUMERIC(36, 18),
    quantity NUMERIC(36, 18),
    reason VARCHAR(256),
    decision_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    risk_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    market_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_replay_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_replay_run_time ON trade_replay_records (paper_run_id, replay_time DESC);

COMMENT ON TABLE trade_replay_records IS 'GateI-4 Paper Trading 交易复盘记录表：保存 Paper run 的交易决策审计链路，用于单笔交易复盘';
COMMENT ON COLUMN trade_replay_records.replay_record_id IS '复盘记录主键，业务可读 ID，例如 trr-<uuid>';
COMMENT ON COLUMN trade_replay_records.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN trade_replay_records.paper_order_id IS '关联订单 ID，可空（非订单事件时为空）';
COMMENT ON COLUMN trade_replay_records.paper_trade_id IS '关联成交 ID，可空（非成交事件时为空）';
COMMENT ON COLUMN trade_replay_records.replay_time IS '事件发生时间，UTC';
COMMENT ON COLUMN trade_replay_records.event_type IS '事件类型，例如 SIGNAL、ORDER_CREATED、ORDER_FILLED、RISK_CHECK、POSITION_UPDATE';
COMMENT ON COLUMN trade_replay_records.symbol IS '事件关联交易对';
COMMENT ON COLUMN trade_replay_records.side IS '方向 BUY/SELL，可空';
COMMENT ON COLUMN trade_replay_records.price IS '价格，可空';
COMMENT ON COLUMN trade_replay_records.quantity IS '数量，可空';
COMMENT ON COLUMN trade_replay_records.reason IS '事件原因摘要';
COMMENT ON COLUMN trade_replay_records.decision_snapshot_json IS '决策快照 JSONB，保存策略信号和参数摘要；不得保存敏感凭证';
COMMENT ON COLUMN trade_replay_records.risk_snapshot_json IS '风控快照 JSONB，保存风控检查输入输出摘要；不得保存敏感凭证';
COMMENT ON COLUMN trade_replay_records.market_snapshot_json IS '市场快照 JSONB，保存事件时刻行情摘要；不得保存敏感凭证';
COMMENT ON COLUMN trade_replay_records.created_at IS '记录写入时间，UTC';

-- ============================================================
-- 5. emergency_stop_events
-- ============================================================
CREATE TABLE emergency_stop_events (
    emergency_stop_id VARCHAR(64) PRIMARY KEY,
    paper_run_id VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(512),
    triggered_by VARCHAR(128),
    triggered_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    request_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_estop_trigger_type CHECK (trigger_type IN ('MANUAL', 'RISK_LIMIT', 'SYSTEM_ERROR')),
    CONSTRAINT chk_estop_status CHECK (status IN ('TRIGGERED', 'APPLIED', 'FAILED', 'RESOLVED')),
    CONSTRAINT fk_estop_run FOREIGN KEY (paper_run_id)
        REFERENCES paper_trading_runs (paper_run_id)
);

CREATE INDEX idx_estop_run_id ON emergency_stop_events (paper_run_id, triggered_at DESC);
CREATE INDEX idx_estop_status ON emergency_stop_events (status, triggered_at DESC);

COMMENT ON TABLE emergency_stop_events IS 'GateI-4 Paper Trading 异常停机事件表：记录 Paper run 的紧急停机触发、执行和解除事实；只作用于 SIM/Paper，不触发真实 LIVE 下单或撤单';
COMMENT ON COLUMN emergency_stop_events.emergency_stop_id IS '异常停机事件主键，业务可读 ID，例如 es-<uuid>';
COMMENT ON COLUMN emergency_stop_events.paper_run_id IS '所属 Paper run ID，外键 paper_trading_runs.paper_run_id';
COMMENT ON COLUMN emergency_stop_events.trigger_type IS '触发类型：MANUAL 手动触发；RISK_LIMIT 风控限额触发；SYSTEM_ERROR 系统异常触发';
COMMENT ON COLUMN emergency_stop_events.status IS '停机状态：TRIGGERED 已触发；APPLIED 已执行（run 已停止）；FAILED 执行失败（run 非 RUNNING）；RESOLVED 已解除';
COMMENT ON COLUMN emergency_stop_events.reason IS '停机原因摘要';
COMMENT ON COLUMN emergency_stop_events.triggered_by IS '触发人标识，来自登录上下文或系统标识';
COMMENT ON COLUMN emergency_stop_events.triggered_at IS '触发时间，UTC';
COMMENT ON COLUMN emergency_stop_events.resolved_at IS '解除时间，UTC；为空表示尚未解除';
COMMENT ON COLUMN emergency_stop_events.request_json IS '停机请求快照 JSONB，保存触发时的请求参数；不得保存敏感凭证';
COMMENT ON COLUMN emergency_stop_events.result_json IS '停机结果快照 JSONB，保存执行结果和错误信息；不得保存敏感凭证';
COMMENT ON COLUMN emergency_stop_events.created_at IS '记录写入时间，UTC';
