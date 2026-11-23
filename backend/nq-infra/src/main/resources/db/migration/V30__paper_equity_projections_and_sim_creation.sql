-- 仅扩展已有创建回执和权益投影；不更新历史金额、币种或Flyway记录。
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='30s';
ALTER TABLE research_creation_operations DROP CONSTRAINT research_creation_operations_kind_check;
ALTER TABLE research_creation_operations ADD CONSTRAINT research_creation_operations_kind_check
 CHECK(kind IN ('STRATEGY','VERSION','RESEARCH','BACKTEST','BACKTEST_RUN','SIM_RUN'));
COMMENT ON COLUMN research_creation_operations.kind IS '本地研究或隔离SIM创建；SIM_RUN只创建独立预算，不启动交易，不扩大LIVE权限';

CREATE UNIQUE INDEX uq_paper_equity_canonical_time ON equity_curve_snapshots
 (paper_run_id,snapshot_time,valuation_method) WHERE source='CANONICAL_SIM_PROJECTION';
ALTER TABLE equity_curve_snapshots ADD CONSTRAINT ck_paper_equity_canonical_projection CHECK (
 source<>'CANONICAL_SIM_PROJECTION' OR ((
 valuation_status='VERIFIED' AND valuation_currency='USDT'
 AND valuation_method='CANONICAL_SIM_LEDGER_AND_FROZEN_MARK_V1'
 AND snapshot_time=valuation_time AND isfinite(snapshot_time) AND isfinite(created_at)
 AND total_equity=cash_balance+position_value AND position_value>=0
 AND drawdown>=0 AND drawdown<=1
 AND total_equity::text NOT IN ('NaN','Infinity','-Infinity')
 AND cash_balance::text NOT IN ('NaN','Infinity','-Infinity')
 AND position_value::text NOT IN ('NaN','Infinity','-Infinity')
 AND realized_pnl::text NOT IN ('NaN','Infinity','-Infinity')
 AND unrealized_pnl::text NOT IN ('NaN','Infinity','-Infinity')
 AND drawdown::text NOT IN ('NaN','Infinity','-Infinity')
 AND valuation_details->>'paperRunId'=paper_run_id
 AND valuation_details->>'valuationCurrency'='USDT'
 AND valuation_details->>'snapshotTime'=to_char(snapshot_time AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
 AND length(valuation_details->>'decisionId')>0
 AND (valuation_details->>'canonicalAccountId')::bigint>0
 ) IS TRUE));

CREATE FUNCTION guard_paper_equity_projection() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP<>'INSERT' AND OLD.source='CANONICAL_SIM_PROJECTION' THEN
   RAISE EXCEPTION 'canonical equity projection is immutable' USING ERRCODE='23514';
 END IF;
 IF TG_OP<>'DELETE' AND NEW.source='CANONICAL_SIM_PROJECTION' THEN
   IF TG_OP='UPDATE' OR NOT EXISTS(
       SELECT 1 FROM paper_trading_runs r JOIN strategy_sim_decisions d
         ON d.paper_run_id=r.paper_run_id AND d.canonical_account_id=r.canonical_account_id
       WHERE r.paper_run_id=NEW.paper_run_id AND r.trade_env='SIM'
         AND r.canonical_account_id=(NEW.valuation_details->>'canonicalAccountId')::bigint
         AND d.decision_id=NEW.valuation_details->>'decisionId') THEN
     RAISE EXCEPTION 'canonical equity source identity missing' USING ERRCODE='23514';
   END IF;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER trg_guard_paper_equity_projection BEFORE INSERT OR UPDATE OR DELETE
 ON equity_curve_snapshots FOR EACH ROW EXECUTE FUNCTION guard_paper_equity_projection();
COMMENT ON FUNCTION guard_paper_equity_projection() IS '可信权益投影必须关联原运行、独立账户和持久决策；只能追加，历史UNKNOWN行不受重写';
COMMENT ON CONSTRAINT ck_paper_equity_canonical_projection ON equity_curve_snapshots IS '新投影的USDT口径、有限金额、金额关系和来源身份；不把旧数据推断为USDT';
COMMENT ON INDEX uq_paper_equity_canonical_time IS '同一运行、估值事件时间和方法只追加一份派生快照，重启与重复请求不另写金额';
