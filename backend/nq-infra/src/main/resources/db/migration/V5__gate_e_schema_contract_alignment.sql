-- V5__gate_e_schema_contract_alignment.sql
-- 目的：落实 GateE-0.2 的最小 schema / metadata / contract 收口。
-- Why:
-- 1) GateE-1.1 需要最小策略定义表与调度配置表，当前 schema 尚未提供；
-- 2) GateE 需要把 strategy/request/order/exchange/env 的身份口径写进数据库，而不仅是文档；
-- 3) 现有 orders/trades/strategy_runs 仍保留 GateD 历史命名，本次只做最小兼容收口，不扩大到服务层重写。

CREATE TABLE strategy_definitions (
    strategy_id VARCHAR(128) PRIMARY KEY,
    strategy_code VARCHAR(128) NOT NULL,
    strategy_name VARCHAR(255) NOT NULL,
    strategy_type VARCHAR(64) NOT NULL,
    exchange_code VARCHAR(32) NOT NULL,
    account_id BIGINT NOT NULL,
    trade_env VARCHAR(8) NOT NULL DEFAULT 'SIM',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_strategy_definitions_strategy_code UNIQUE (strategy_code),
    CONSTRAINT fk_strategy_definitions_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT chk_strategy_definitions_trade_env CHECK (trade_env IN ('SIM', 'LIVE')),
    CONSTRAINT chk_strategy_definitions_version CHECK (version > 0)
);

CREATE INDEX idx_strategy_definitions_enabled_scan
    ON strategy_definitions (exchange_code, account_id, trade_env, enabled, updated_at DESC);

CREATE TABLE strategy_schedules (
    schedule_job_id VARCHAR(128) PRIMARY KEY,
    strategy_id VARCHAR(128) NOT NULL,
    schedule_type VARCHAR(32) NOT NULL DEFAULT 'CRON',
    cron_expr VARCHAR(128),
    timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    window_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    dedup_scope VARCHAR(32) NOT NULL DEFAULT 'SCHEDULE_WINDOW',
    exchange_code VARCHAR(32) NOT NULL,
    account_id BIGINT NOT NULL,
    trade_env VARCHAR(8) NOT NULL DEFAULT 'SIM',
    last_triggered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_strategy_schedules_strategy FOREIGN KEY (strategy_id) REFERENCES strategy_definitions (strategy_id),
    CONSTRAINT fk_strategy_schedules_account FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CONSTRAINT chk_strategy_schedules_trade_env CHECK (trade_env IN ('SIM', 'LIVE')),
    CONSTRAINT chk_strategy_schedules_type CHECK (schedule_type IN ('CRON', 'INTERVAL', 'MANUAL')),
    CONSTRAINT chk_strategy_schedules_dedup_scope CHECK (dedup_scope IN ('SCHEDULE_WINDOW', 'REQUEST', 'STRATEGY'))
);

CREATE INDEX idx_strategy_schedules_strategy_enabled
    ON strategy_schedules (strategy_id, enabled, updated_at DESC);

CREATE INDEX idx_strategy_schedules_enabled_scan
    ON strategy_schedules (enabled, exchange_code, account_id, trade_env, last_triggered_at);

ALTER TABLE strategy_runs
    RENAME COLUMN run_id TO strategy_run_id;

ALTER TABLE strategy_runs
    RENAME COLUMN ended_at TO finished_at;

ALTER TABLE strategy_runs
    ADD COLUMN trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN exchange_code VARCHAR(32),
    ADD COLUMN trade_env VARCHAR(8) NOT NULL DEFAULT 'SIM',
    ADD COLUMN config_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN request_id VARCHAR(64),
    ADD COLUMN error_message TEXT;

UPDATE strategy_runs AS sr
SET exchange_code = a.venue
FROM accounts AS a
WHERE sr.account_id = a.account_id
  AND sr.exchange_code IS NULL;

ALTER TABLE strategy_runs
    ALTER COLUMN exchange_code SET NOT NULL;

ALTER TABLE strategy_runs
    ADD CONSTRAINT chk_strategy_runs_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'SCHEDULER', 'RECOVERY'));

ALTER TABLE strategy_runs
    ADD CONSTRAINT chk_strategy_runs_trade_env
        CHECK (trade_env IN ('SIM', 'LIVE'));

CREATE INDEX idx_strategy_runs_request_id
    ON strategy_runs (request_id)
    WHERE request_id IS NOT NULL;

CREATE INDEX idx_strategy_runs_exchange_account_status
    ON strategy_runs (exchange_code, account_id, trade_env, status, started_at DESC);

ALTER TABLE orders
    ADD COLUMN request_id VARCHAR(64),
    ADD COLUMN dedup_key VARCHAR(128),
    ADD COLUMN exchange_code VARCHAR(32),
    ADD COLUMN trade_env VARCHAR(8) NOT NULL DEFAULT 'SIM',
    ADD COLUMN exchange_order_id VARCHAR(128);

UPDATE orders
SET request_id = trace_id
WHERE request_id IS NULL;

UPDATE orders
SET dedup_key = account_id::TEXT || ':' || client_order_id
WHERE dedup_key IS NULL;

UPDATE orders
SET exchange_code = venue
WHERE exchange_code IS NULL
  AND venue IS NOT NULL;

UPDATE orders
SET exchange_order_id = external_order_id
WHERE exchange_order_id IS NULL
  AND external_order_id IS NOT NULL;

ALTER TABLE orders
    ALTER COLUMN exchange_code SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_trade_env
        CHECK (trade_env IN ('SIM', 'LIVE'));

CREATE INDEX idx_orders_request_id
    ON orders (request_id);

CREATE UNIQUE INDEX uq_orders_account_dedup_key
    ON orders (account_id, dedup_key)
    WHERE dedup_key IS NOT NULL;

CREATE INDEX idx_orders_exchange_code_exchange_order_id
    ON orders (exchange_code, exchange_order_id)
    WHERE exchange_order_id IS NOT NULL;

ALTER TABLE trades
    ADD COLUMN strategy_run_id VARCHAR(64),
    ADD COLUMN exchange_code VARCHAR(32),
    ADD COLUMN trade_env VARCHAR(8) NOT NULL DEFAULT 'SIM',
    ADD COLUMN exchange_order_id VARCHAR(128);

UPDATE trades AS t
SET strategy_run_id = o.strategy_run_id,
    exchange_code = COALESCE(t.exchange_code, o.exchange_code, o.venue),
    trade_env = COALESCE(t.trade_env, o.trade_env, 'SIM'),
    exchange_order_id = COALESCE(t.exchange_order_id, t.external_order_id, o.exchange_order_id, o.external_order_id)
FROM orders AS o
WHERE t.order_id = o.order_id;

UPDATE trades
SET exchange_code = exchange
WHERE exchange_code IS NULL
  AND exchange IS NOT NULL;

UPDATE trades
SET exchange_order_id = external_order_id
WHERE exchange_order_id IS NULL
  AND external_order_id IS NOT NULL;

ALTER TABLE trades
    ALTER COLUMN exchange_code SET NOT NULL;

ALTER TABLE trades
    ADD CONSTRAINT chk_trades_trade_env
        CHECK (trade_env IN ('SIM', 'LIVE'));

ALTER TABLE trades
    ADD CONSTRAINT fk_trades_strategy_run
        FOREIGN KEY (strategy_run_id) REFERENCES strategy_runs (strategy_run_id);

CREATE INDEX idx_trades_strategy_run_id
    ON trades (strategy_run_id)
    WHERE strategy_run_id IS NOT NULL;

CREATE INDEX idx_trades_exchange_code_exchange_order_id
    ON trades (exchange_code, exchange_order_id)
    WHERE exchange_order_id IS NOT NULL;

CREATE UNIQUE INDEX uq_trades_exchange_code_exchange_trade_id
    ON trades (exchange_code, exchange_trade_id)
    WHERE exchange_trade_id IS NOT NULL;

CREATE OR REPLACE FUNCTION nq_sync_orders_gatee_metadata()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.request_id IS NULL OR BTRIM(NEW.request_id) = '' THEN
        NEW.request_id := NEW.trace_id;
    END IF;
    IF NEW.dedup_key IS NULL OR BTRIM(NEW.dedup_key) = '' THEN
        NEW.dedup_key := NEW.account_id::TEXT || ':' || NEW.client_order_id;
    END IF;
    IF NEW.exchange_code IS NULL OR BTRIM(NEW.exchange_code) = '' THEN
        NEW.exchange_code := NEW.venue;
    END IF;
    IF NEW.trade_env IS NULL OR BTRIM(NEW.trade_env) = '' THEN
        NEW.trade_env := 'SIM';
    END IF;
    IF (NEW.exchange_order_id IS NULL OR BTRIM(NEW.exchange_order_id) = '')
        AND NEW.external_order_id IS NOT NULL AND BTRIM(NEW.external_order_id) <> '' THEN
        NEW.exchange_order_id := NEW.external_order_id;
    END IF;
    IF (NEW.external_order_id IS NULL OR BTRIM(NEW.external_order_id) = '')
        AND NEW.exchange_order_id IS NOT NULL AND BTRIM(NEW.exchange_order_id) <> '' THEN
        NEW.external_order_id := NEW.exchange_order_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_orders_gatee_metadata ON orders;

CREATE TRIGGER trg_orders_gatee_metadata
    BEFORE INSERT OR UPDATE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION nq_sync_orders_gatee_metadata();

CREATE OR REPLACE FUNCTION nq_sync_trades_gatee_metadata()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    order_exchange_code VARCHAR(32);
    order_trade_env VARCHAR(8);
    order_strategy_run_id VARCHAR(128);
    order_exchange_order_id VARCHAR(128);
BEGIN
    SELECT o.exchange_code, o.trade_env, o.strategy_run_id, o.exchange_order_id
      INTO order_exchange_code, order_trade_env, order_strategy_run_id, order_exchange_order_id
      FROM orders AS o
     WHERE o.order_id = NEW.order_id;

    IF NEW.exchange_code IS NULL OR BTRIM(NEW.exchange_code) = '' THEN
        NEW.exchange_code := COALESCE(order_exchange_code, NEW.exchange);
    END IF;
    IF NEW.trade_env IS NULL OR BTRIM(NEW.trade_env) = '' THEN
        NEW.trade_env := COALESCE(order_trade_env, 'SIM');
    END IF;
    IF (NEW.strategy_run_id IS NULL OR BTRIM(NEW.strategy_run_id) = '') AND order_strategy_run_id IS NOT NULL THEN
        NEW.strategy_run_id := order_strategy_run_id;
    END IF;
    IF (NEW.exchange_order_id IS NULL OR BTRIM(NEW.exchange_order_id) = '') THEN
        NEW.exchange_order_id := COALESCE(NEW.external_order_id, order_exchange_order_id);
    END IF;
    IF (NEW.external_order_id IS NULL OR BTRIM(NEW.external_order_id) = '')
        AND NEW.exchange_order_id IS NOT NULL AND BTRIM(NEW.exchange_order_id) <> '' THEN
        NEW.external_order_id := NEW.exchange_order_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_trades_gatee_metadata ON trades;

CREATE TRIGGER trg_trades_gatee_metadata
    BEFORE INSERT OR UPDATE ON trades
    FOR EACH ROW
    EXECUTE FUNCTION nq_sync_trades_gatee_metadata();

COMMENT ON TABLE strategy_definitions IS 'GateE 策略定义主表。保存策略注册项、启停状态和配置快照，不引入 strategyInstanceId。';
COMMENT ON COLUMN strategy_definitions.strategy_id IS '策略定义主键，定义级身份，不代表单次运行。';
COMMENT ON COLUMN strategy_definitions.strategy_code IS '策略注册业务唯一键。一个启用中的策略注册项按 strategy_code 唯一识别。';
COMMENT ON COLUMN strategy_definitions.strategy_name IS '策略展示名称，用于管理界面和审计定位。';
COMMENT ON COLUMN strategy_definitions.strategy_type IS '策略类型，例如 GRID、DEMO、MOMENTUM。';
COMMENT ON COLUMN strategy_definitions.exchange_code IS '统一交易所标识，固定口径为 OKX / BINANCE / PAPER 等。';
COMMENT ON COLUMN strategy_definitions.account_id IS '策略绑定账户 ID，区分同策略在不同账户下的注册项。';
COMMENT ON COLUMN strategy_definitions.trade_env IS '交易环境，固定枚举为 SIM / LIVE。';
COMMENT ON COLUMN strategy_definitions.enabled IS '策略注册启停开关。默认 FALSE，避免注册后立即进入运行。';
COMMENT ON COLUMN strategy_definitions.config_snapshot IS '策略定义级配置快照，JSONB 保存当前生效配置。';
COMMENT ON COLUMN strategy_definitions.version IS '策略定义版本号。每次配置变更时递增，用于配置快照审计。';
COMMENT ON COLUMN strategy_definitions.created_at IS '策略定义创建时间。';
COMMENT ON COLUMN strategy_definitions.updated_at IS '策略定义最后更新时间。';

COMMENT ON TABLE strategy_schedules IS 'GateE 调度配置表。保存策略与 schedule job 的最小关系，不承载 trigger 实例表。';
COMMENT ON COLUMN strategy_schedules.schedule_job_id IS '调度作业主键，调度级身份。';
COMMENT ON COLUMN strategy_schedules.strategy_id IS '所属策略定义 ID，定义级外键。';
COMMENT ON COLUMN strategy_schedules.schedule_type IS '调度类型。当前允许 CRON / INTERVAL / MANUAL。';
COMMENT ON COLUMN strategy_schedules.cron_expr IS 'CRON 表达式。schedule_type=CRON 时使用。';
COMMENT ON COLUMN strategy_schedules.timezone IS '调度时区。默认 UTC。';
COMMENT ON COLUMN strategy_schedules.enabled IS '调度启停开关。默认 FALSE。';
COMMENT ON COLUMN strategy_schedules.window_config IS '运行窗口配置快照，JSONB。';
COMMENT ON COLUMN strategy_schedules.dedup_scope IS '调度去重范围，控制同一策略在窗口内如何去重。';
COMMENT ON COLUMN strategy_schedules.exchange_code IS '统一交易所标识，供调度扫描和路由使用。';
COMMENT ON COLUMN strategy_schedules.account_id IS '调度绑定账户 ID。';
COMMENT ON COLUMN strategy_schedules.trade_env IS '交易环境，固定枚举为 SIM / LIVE。';
COMMENT ON COLUMN strategy_schedules.last_triggered_at IS '最近一次成功触发时间，用于调度扫描与排障。';
COMMENT ON COLUMN strategy_schedules.created_at IS '调度配置创建时间。';
COMMENT ON COLUMN strategy_schedules.updated_at IS '调度配置最后更新时间。';

COMMENT ON TABLE strategy_runs IS 'GateE 单次策略运行表。strategy_run_id 是运行级身份，request_id 仅保存首次触发请求身份。';
COMMENT ON COLUMN strategy_runs.strategy_run_id IS '策略运行主键，运行级身份。';
COMMENT ON COLUMN strategy_runs.strategy_id IS '所属策略定义 ID，定义级身份。';
COMMENT ON COLUMN strategy_runs.account_id IS '本次运行绑定账户 ID。';
COMMENT ON COLUMN strategy_runs.status IS '策略运行状态，例如 CREATED / RUNNING / SUCCEEDED / FAILED。';
COMMENT ON COLUMN strategy_runs.trigger_type IS '运行触发来源，固定口径为 MANUAL / SCHEDULER / RECOVERY。';
COMMENT ON COLUMN strategy_runs.exchange_code IS '本次运行对应交易所标识。';
COMMENT ON COLUMN strategy_runs.trade_env IS '本次运行对应交易环境，固定枚举为 SIM / LIVE。';
COMMENT ON COLUMN strategy_runs.config_snapshot IS '运行时配置快照。与策略定义快照分离，便于复盘单次运行。';
COMMENT ON COLUMN strategy_runs.request_id IS '首次接受的执行请求 ID。属于请求级身份，不等同于 strategy_run_id。';
COMMENT ON COLUMN strategy_runs.started_at IS '运行开始时间。';
COMMENT ON COLUMN strategy_runs.finished_at IS '运行结束时间。历史字段 ended_at 已收口为 finished_at。';
COMMENT ON COLUMN strategy_runs.error_message IS '运行终态错误摘要，仅记录最终可见错误信息。';
COMMENT ON COLUMN strategy_runs.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN strategy_runs.created_at IS '运行记录创建时间。';

COMMENT ON TABLE orders IS '订单事实表。order_id 是内部主键，strategy_run_id 负责与 GateE 运行血缘关联。';
COMMENT ON COLUMN orders.order_id IS '内部订单主键，系统侧唯一身份。';
COMMENT ON COLUMN orders.account_id IS '订单所属账户 ID。';
COMMENT ON COLUMN orders.strategy_run_id IS '所属策略运行 ID，运行级血缘外键，可空。';
COMMENT ON COLUMN orders.venue IS '历史兼容列。当前仍被代码使用，语义等同 exchange_code，后续逐步迁移。';
COMMENT ON COLUMN orders.exchange_code IS '统一交易所标识，GateE 之后的新口径字段。';
COMMENT ON COLUMN orders.trade_env IS '交易环境，固定枚举为 SIM / LIVE。当前兼容阶段默认 SIM。';
COMMENT ON COLUMN orders.symbol IS '内部交易对标识。';
COMMENT ON COLUMN orders.client_order_id IS '客户端订单号 / 幂等业务号。';
COMMENT ON COLUMN orders.dedup_key IS '订单去重键。当前默认使用 account_id:client_order_id。';
COMMENT ON COLUMN orders.request_id IS '执行请求级身份。一个策略运行可对应多个 request_id。';
COMMENT ON COLUMN orders.side IS '订单方向。';
COMMENT ON COLUMN orders.type IS '订单类型。';
COMMENT ON COLUMN orders.price IS '订单价格。';
COMMENT ON COLUMN orders.qty IS '订单数量。';
COMMENT ON COLUMN orders.external_order_id IS '历史兼容列。语义等同 exchange_order_id，后续逐步迁移。';
COMMENT ON COLUMN orders.exchange_order_id IS '交易所订单号，外部订单身份。';
COMMENT ON COLUMN orders.status IS '订单状态。继续沿用 GateD 状态机。';
COMMENT ON COLUMN orders.reason IS '状态推进原因或拒绝原因。';
COMMENT ON COLUMN orders.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN orders.created_at IS '订单创建时间。';
COMMENT ON COLUMN orders.updated_at IS '订单最后更新时间。';

COMMENT ON TABLE trades IS '成交事实表。trade_id 是内部主键，exchange_trade_id 是交易所成交号。';
COMMENT ON COLUMN trades.trade_id IS '内部成交主键。';
COMMENT ON COLUMN trades.order_id IS '所属内部订单 ID。';
COMMENT ON COLUMN trades.strategy_run_id IS '所属策略运行 ID，便于按运行直接反查成交血缘。';
COMMENT ON COLUMN trades.account_id IS '所属账户 ID。';
COMMENT ON COLUMN trades.symbol IS '内部交易对标识。';
COMMENT ON COLUMN trades.exchange IS '历史兼容列。当前仍被代码使用，语义等同 exchange_code。';
COMMENT ON COLUMN trades.exchange_code IS '统一交易所标识，GateE 之后的新口径字段。';
COMMENT ON COLUMN trades.trade_env IS '交易环境，固定枚举为 SIM / LIVE。当前兼容阶段默认 SIM。';
COMMENT ON COLUMN trades.external_order_id IS '历史兼容列。语义等同 exchange_order_id。';
COMMENT ON COLUMN trades.exchange_order_id IS '交易所订单号，便于按订单维度回溯成交。';
COMMENT ON COLUMN trades.exchange_trade_id IS '交易所成交号，参与成交去重。';
COMMENT ON COLUMN trades.price IS '成交价格。';
COMMENT ON COLUMN trades.qty IS '成交数量。';
COMMENT ON COLUMN trades.fee IS '成交手续费。';
COMMENT ON COLUMN trades.fee_currency IS '手续费币种。';
COMMENT ON COLUMN trades.trace_id IS '链路追踪 ID。';
COMMENT ON COLUMN trades.ts IS '成交事实时间。';
COMMENT ON COLUMN trades.created_at IS '成交记录创建时间。';
