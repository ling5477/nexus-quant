-- 只为新隔离 SIM 保存不可变初始化身份；当前资金唯一来源仍是 canonical Ledger。
-- V1—V13 保持原样，无历史回填，无真实买币或资金转账。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

CREATE TABLE sim_asset_funding_receipts (
    paper_run_id varchar(64) PRIMARY KEY REFERENCES paper_trading_runs(paper_run_id) ON DELETE RESTRICT,
    account_id bigint NOT NULL UNIQUE REFERENCES accounts(account_id) ON DELETE RESTRICT,
    total_budget numeric(38,8) NOT NULL,
    quote_currency varchar(20) NOT NULL,
    asset_currency varchar(20) NOT NULL,
    asset_budget_fraction numeric(18,18) NOT NULL,
    conversion_price numeric(38,18) NOT NULL,
    conversion_price_unit varchar(40) NOT NULL,
    observed_at timestamptz NOT NULL,
    visible_at timestamptz NOT NULL,
    as_of timestamptz NOT NULL,
    source_sha256 varchar(64) NOT NULL,
    source_json text NOT NULL,
    funded_at timestamptz NOT NULL,
    trace_id varchar(64) NOT NULL,
    cash_entry_id varchar(64) NOT NULL UNIQUE REFERENCES ledger_entries(entry_id) ON DELETE RESTRICT,
    cash_contra_entry_id varchar(64) NOT NULL UNIQUE REFERENCES ledger_entries(entry_id) ON DELETE RESTRICT,
    asset_entry_id varchar(64) NOT NULL UNIQUE REFERENCES ledger_entries(entry_id) ON DELETE RESTRICT,
    asset_contra_entry_id varchar(64) NOT NULL UNIQUE REFERENCES ledger_entries(entry_id) ON DELETE RESTRICT,
    CONSTRAINT chk_sim_asset_funding_budget CHECK (total_budget>0 AND total_budget<=100000
        AND asset_budget_fraction>0 AND asset_budget_fraction<1 AND conversion_price>0
        AND trunc(total_budget*asset_budget_fraction/conversion_price,8)>0
        AND round(trunc(total_budget*asset_budget_fraction/conversion_price,8)*conversion_price,8)>0
        AND round(trunc(total_budget*asset_budget_fraction/conversion_price,8)*conversion_price,8)<=total_budget),
    CONSTRAINT chk_sim_asset_funding_currency CHECK (quote_currency='USDT' AND asset_currency<>quote_currency
        AND asset_currency ~ '^[A-Z0-9]{2,20}$' AND conversion_price_unit='USDT_PER_'||asset_currency),
    CONSTRAINT chk_sim_asset_funding_time CHECK (observed_at<=visible_at AND visible_at<=as_of),
    CONSTRAINT chk_sim_asset_funding_source CHECK (source_sha256 ~ '^[a-f0-9]{64}$'
        AND octet_length(source_json)<=65536 AND jsonb_typeof(source_json::jsonb)='object'
        AND source_json::jsonb<>'{}'::jsonb
        AND source_sha256=encode(digest(convert_to(source_json,'UTF8'),'sha256'),'hex')),
    CONSTRAINT chk_sim_asset_funding_identity CHECK (btrim(paper_run_id)<>'' AND btrim(trace_id)<>''
        AND cash_entry_id<>cash_contra_entry_id AND cash_entry_id<>asset_entry_id AND cash_entry_id<>asset_contra_entry_id
        AND cash_contra_entry_id<>asset_entry_id AND cash_contra_entry_id<>asset_contra_entry_id AND asset_entry_id<>asset_contra_entry_id)
);

CREATE FUNCTION guard_sim_asset_funding_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    v_quantity numeric;
    v_cash numeric;
    v_expected record;
BEGIN
    IF TG_OP<>'INSERT' THEN
        RAISE EXCEPTION 'SIM asset funding identity is immutable' USING ERRCODE='23514';
    END IF;
    -- 绑定与账户行锁阻止初始化同时启动 run 或更换账户；不接受真实交易所账户。
    PERFORM 1 FROM paper_trading_runs p JOIN accounts a ON a.account_id=p.canonical_account_id
    WHERE p.paper_run_id=NEW.paper_run_id AND p.canonical_account_id=NEW.account_id
      AND p.trade_env='SIM' AND p.status='CREATED' AND a.venue='PAPER' AND a.status='ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM exchange_accounts x WHERE x.legacy_account_id=a.account_id)
    FOR UPDATE OF p,a;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'SIM asset funding requires bound new PAPER account' USING ERRCODE='23514';
    END IF;
    v_quantity:=trunc(NEW.total_budget*NEW.asset_budget_fraction/NEW.conversion_price,8);
    v_cash:=NEW.total_budget-round(v_quantity*NEW.conversion_price,8);
    FOR v_expected IN SELECT * FROM (VALUES
        (NEW.cash_entry_id,NEW.quote_currency,v_cash,v_cash,'CASH','SIM_FUNDING_CASH'),
        (NEW.cash_contra_entry_id,NEW.quote_currency,-v_cash,v_cash,'CONTRA','SIM_FUNDING_CONTRA'),
        (NEW.asset_entry_id,NEW.asset_currency,v_quantity,v_quantity,'CASH','SIM_FUNDING_CASH'),
        (NEW.asset_contra_entry_id,NEW.asset_currency,-v_quantity,v_quantity,'CONTRA','SIM_FUNDING_CONTRA')
    ) AS expected(entry_id,currency,delta,balance,role,ref_type) LOOP
        PERFORM 1 FROM ledger_entries e WHERE e.entry_id=v_expected.entry_id AND e.account_id=NEW.account_id
          AND e.currency=v_expected.currency AND e.delta=v_expected.delta AND e.balance_after=v_expected.balance
          AND e.direction=CASE WHEN v_expected.delta>=0 THEN 'CREDIT' ELSE 'DEBIT' END
          AND e.ref_type=v_expected.ref_type AND e.ref_id=NEW.paper_run_id
          AND e.idempotency_key=NEW.paper_run_id||':SIM_FUND:'||v_expected.currency||':'||v_expected.role
          AND e.trace_id=NEW.trace_id AND e.ts=NEW.funded_at
          AND (SELECT count(*) FROM ledger_events v WHERE v.entry_id=e.entry_id AND v.event_type='POSTED'
              AND v.trace_id=NEW.trace_id AND v.payload_json=jsonb_build_object('source','STRATEGY_SIM_ASSET_FUNDING',
                  'sourceSha256',NEW.source_sha256,'paperRunId',NEW.paper_run_id,'currency',v_expected.currency,'role',v_expected.role))=1;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'SIM asset funding canonical posting is incomplete' USING ERRCODE='23514';
        END IF;
        IF v_expected.role='CASH' AND (SELECT count(*) FROM account_snapshots s
            WHERE s.account_id=NEW.account_id AND s.currency=v_expected.currency
              AND s.balance=v_expected.balance AND s.available=v_expected.balance AND s.frozen=0
              AND s.trade_env='SIM' AND s.balance_basis='LEDGER_CASH_PROJECTION' AND s.balance_scope='NQ_MANAGED_ACCOUNT'
              AND s.ts=NEW.funded_at AND s.trace_id=NEW.trace_id AND s.recorded_at IS NOT NULL)<>1 THEN
            RAISE EXCEPTION 'SIM asset funding snapshot is incomplete' USING ERRCODE='23514';
        END IF;
    END LOOP;
    -- receipt 不是另一本账，禁止把既有账户事实混入首次资金来源。
    IF (SELECT count(*) FROM ledger_entries WHERE account_id=NEW.account_id)<>4
        OR (SELECT count(*) FROM account_snapshots WHERE account_id=NEW.account_id)<>2
        OR EXISTS(SELECT 1 FROM positions WHERE account_id=NEW.account_id)
        OR EXISTS(SELECT 1 FROM orders WHERE account_id=NEW.account_id)
        OR EXISTS(SELECT 1 FROM trades WHERE account_id=NEW.account_id) THEN
        RAISE EXCEPTION 'SIM asset funding account is not empty' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_sim_asset_funding_receipt BEFORE INSERT OR UPDATE OR DELETE ON sim_asset_funding_receipts
FOR EACH ROW EXECUTE FUNCTION guard_sim_asset_funding_receipt();

COMMENT ON TABLE sim_asset_funding_receipts IS '新隔离 SIM 总预算拆分与来源的不可变收据；不保存当前余额，不是第二账务事实源';
COMMENT ON COLUMN sim_asset_funding_receipts.paper_run_id IS '稳定 SIM run 身份；重复请求必须复用完整预算和来源';
COMMENT ON COLUMN sim_asset_funding_receipts.account_id IS '已绑定的独立 PAPER/SIM canonical 账户，不允许真实交易所账户';
COMMENT ON COLUMN sim_asset_funding_receipts.total_budget IS '用户声明的总 USDT 预算；现金与优惠资产初始报价估值之和保持一致';
COMMENT ON COLUMN sim_asset_funding_receipts.quote_currency IS '总预算及估值单位固定 USDT';
COMMENT ON COLUMN sim_asset_funding_receipts.asset_currency IS '独立优惠资产；原币数量只从 canonical Ledger 获取';
COMMENT ON COLUMN sim_asset_funding_receipts.asset_budget_fraction IS '新 run 冻结预算份额；后续偏好更改不能重置既有 run';
COMMENT ON COLUMN sim_asset_funding_receipts.conversion_price IS '初始拆分换算价；原币数量向下八位，报价估值四舍五入八位';
COMMENT ON COLUMN sim_asset_funding_receipts.conversion_price_unit IS '显式 USDT_PER_资产单位，禁止反向价格混用';
COMMENT ON COLUMN sim_asset_funding_receipts.observed_at IS '正式来源观察时点；真实性及新鲜度仍需上层核验';
COMMENT ON COLUMN sim_asset_funding_receipts.visible_at IS '换算输入首次可见时点，不得晚于本次使用时点';
COMMENT ON COLUMN sim_asset_funding_receipts.as_of IS '冻结使用时点；不能用未来可见的数据初始化';
COMMENT ON COLUMN sim_asset_funding_receipts.source_sha256 IS '原 JSON UTF-8 文本的小写 SHA-256，只证明身份一致，不证明来源真实性';
COMMENT ON COLUMN sim_asset_funding_receipts.source_json IS '完整来源原文本，保留字节身份，不经 JSONB 重排后重新生成摘要';
COMMENT ON COLUMN sim_asset_funding_receipts.funded_at IS 'canonical 初始化分录及投影共同的微秒时点';
COMMENT ON COLUMN sim_asset_funding_receipts.trace_id IS '首次资金初始化追踪身份；重放不改写原追踪或分录';
COMMENT ON COLUMN sim_asset_funding_receipts.cash_entry_id IS 'USDT 正向资金分录关联，不代表当前现金余额';
COMMENT ON COLUMN sim_asset_funding_receipts.cash_contra_entry_id IS 'USDT 平衡对手分录关联';
COMMENT ON COLUMN sim_asset_funding_receipts.asset_entry_id IS '优惠资产正向资金分录关联，不代表当前资产余额';
COMMENT ON COLUMN sim_asset_funding_receipts.asset_contra_entry_id IS '优惠资产平衡对手分录关联';
COMMENT ON FUNCTION guard_sim_asset_funding_receipt() IS '校验已绑定新空 PAPER/SIM 的完整 canonical 初始化，禁止收据更新或删除';
