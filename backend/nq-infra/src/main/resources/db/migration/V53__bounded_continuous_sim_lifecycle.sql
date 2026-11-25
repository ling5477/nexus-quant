SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

-- NULL 截止属于历史开放式合同，已有运行和操作回执无需回填。
ALTER TABLE continuous_sim_runs
    ADD COLUMN end_at timestamptz,
    ADD COLUMN manual_end_at timestamptz,
    ADD COLUMN lifecycle_status varchar(24),
    ADD COLUMN final_valuation_status varchar(16),
    ADD COLUMN last_valuation_boundary timestamptz,
    ADD COLUMN pending_orders integer,
    ADD COLUMN revision_detected_at timestamptz,
    ADD CONSTRAINT ck_continuous_run_deadline CHECK (
        CASE WHEN end_at IS NULL THEN
            num_nonnulls(manual_end_at,lifecycle_status,final_valuation_status,
                         last_valuation_boundary,pending_orders,revision_detected_at)=0
        ELSE (isfinite(end_at) AND end_at>started_at AND end_at<=started_at+interval '2160 hours'
            AND date_trunc('hour',end_at AT TIME ZONE 'UTC')=end_at AT TIME ZONE 'UTC'
            AND lifecycle_status IN ('RUNNING','ADMISSION_CLOSED','SETTLING','COMPLETED','BLOCKED','MANUALLY_STOPPED')
            AND final_valuation_status IN ('PENDING','VERIFIED','UNAVAILABLE')
            AND (final_valuation_status='VERIFIED')=(last_valuation_boundary IS NOT NULL)
            AND (last_valuation_boundary IS NULL OR last_valuation_boundary=end_at)
            AND (pending_orders IS NULL OR pending_orders>=0)
            AND (manual_end_at IS NULL OR (isfinite(manual_end_at) AND lifecycle_status<>'RUNNING'))
            AND (status<>'STOPPED' OR (lifecycle_status IN ('COMPLETED','BLOCKED','MANUALLY_STOPPED')
                 AND pending_orders=0 AND final_valuation_status<>'PENDING'))
            AND (lifecycle_status NOT IN ('COMPLETED','MANUALLY_STOPPED') OR (status='STOPPED' AND pending_orders=0))
            AND (lifecycle_status<>'COMPLETED' OR (manual_end_at IS NULL AND final_valuation_status='VERIFIED'))
            AND (lifecycle_status<>'MANUALLY_STOPPED' OR manual_end_at IS NOT NULL)
        ) IS TRUE END
    );

CREATE FUNCTION nq_guard_continuous_lifecycle() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.end_at IS DISTINCT FROM NEW.end_at OR (OLD.end_at IS NOT NULL AND (
        NEW.started_at IS DISTINCT FROM OLD.started_at OR
        (OLD.manual_end_at IS NOT NULL AND NEW.manual_end_at IS DISTINCT FROM OLD.manual_end_at) OR
        (OLD.revision_detected_at IS NOT NULL AND NEW.revision_detected_at IS DISTINCT FROM OLD.revision_detected_at) OR
        (OLD.status='STOPPED' AND NEW IS DISTINCT FROM OLD) OR
        (OLD.lifecycle_status<>'RUNNING' AND NEW.lifecycle_status='RUNNING')
    )) THEN
        RAISE EXCEPTION 'CONTINUOUS_SIM_IMMUTABLE_LIFECYCLE' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_continuous_lifecycle BEFORE UPDATE ON continuous_sim_runs
FOR EACH ROW EXECUTE FUNCTION nq_guard_continuous_lifecycle();

-- 父运行与连续运行入口共用同一停止意图，行锁将人工命令与真正的经济准入串行化。
CREATE FUNCTION nq_request_continuous_end(target_run text, requested_at timestamptz) RETURNS boolean
LANGUAGE plpgsql AS $$
DECLARE bound continuous_sim_runs%ROWTYPE;
BEGIN
    SELECT * INTO bound FROM continuous_sim_runs
        WHERE paper_run_id=target_run AND end_at IS NOT NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    IF bound.status<>'STOPPED' AND bound.manual_end_at IS NULL THEN
        UPDATE continuous_sim_runs SET manual_end_at=requested_at, lifecycle_status='ADMISSION_CLOSED',
            updated_at=requested_at WHERE paper_run_id=target_run;
    END IF;
    RETURN true;
END;
$$;

-- 独立 canonical 账户已有唯一索引。仅锁住关联的有界运行，收敛已有成交和账本不走新订单准入。
CREATE FUNCTION nq_guard_bounded_sim_admission() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE bound continuous_sim_runs%ROWTYPE;
BEGIN
    IF TG_TABLE_NAME='strategy_sim_decisions' THEN
        SELECT * INTO bound FROM continuous_sim_runs WHERE paper_run_id=NEW.paper_run_id
            AND end_at IS NOT NULL FOR SHARE;
    ELSE
        IF NEW.trade_env<>'SIM' THEN RETURN NEW; END IF;
        SELECT c.* INTO bound FROM continuous_sim_runs c
            JOIN paper_trading_runs p USING(paper_run_id)
            WHERE p.canonical_account_id=NEW.account_id AND c.end_at IS NOT NULL FOR SHARE OF c;
    END IF;
    IF NOT FOUND THEN RETURN NEW; END IF;
    IF TG_TABLE_NAME='strategy_sim_decisions' THEN
        IF NEW.signal_open_time>=bound.end_at OR (NEW.reason='DECIDING' AND
            (NEW.execution_open_time IS NULL OR NEW.execution_open_time<bound.started_at OR NEW.execution_open_time>=bound.end_at)) THEN
            RAISE EXCEPTION 'CONTINUOUS_SIM_ADMISSION_CLOSED' USING ERRCODE='23514';
        END IF;
        IF bound.status<>'STOPPED' AND bound.manual_end_at IS NULL AND bound.revision_detected_at IS NULL
           AND (clock_timestamp()>=bound.end_at OR bound.lifecycle_status<>'RUNNING')
           AND NEW.signal_open_time=bound.end_at-interval '1 hour'
           AND NEW.status IN ('NO_SIGNAL','NOT_TRADABLE') AND NEW.reason<>'DECIDING'
           AND NEW.order_id IS NULL AND NEW.strategy_run_id IS NULL THEN RETURN NEW; END IF;
    END IF;
    IF bound.lifecycle_status<>'RUNNING' OR bound.manual_end_at IS NOT NULL
       OR clock_timestamp()>=bound.end_at THEN
        RAISE EXCEPTION 'CONTINUOUS_SIM_ADMISSION_CLOSED' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_bounded_order_creation BEFORE INSERT ON orders
FOR EACH ROW EXECUTE FUNCTION nq_guard_bounded_sim_admission();
CREATE TRIGGER trg_bounded_order_acceptance BEFORE UPDATE OF status ON orders
FOR EACH ROW WHEN (NEW.status='ACCEPTED' AND OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION nq_guard_bounded_sim_admission();
CREATE TRIGGER trg_bounded_strategy_admission BEFORE INSERT ON strategy_runs
FOR EACH ROW EXECUTE FUNCTION nq_guard_bounded_sim_admission();
CREATE TRIGGER trg_bounded_decision BEFORE INSERT ON strategy_sim_decisions
FOR EACH ROW EXECUTE FUNCTION nq_guard_bounded_sim_admission();

ALTER TABLE continuous_sim_operations ADD COLUMN requested_end_at timestamptz;
ALTER TABLE continuous_sim_operations DROP CONSTRAINT chk_continuous_operation_input,
    ADD CONSTRAINT chk_continuous_operation_input CHECK ((
        (operation_type='CREATE' AND requested_publish_id IS NOT NULL AND requested_budget>0 AND requested_budget<=100000
         AND (requested_end_at IS NULL OR (isfinite(requested_end_at)
              AND requested_end_at=(result_json->'bounded'->>'endAt')::timestamptz)))
        OR (operation_type IN ('PAUSE','RESUME','END') AND requested_publish_id IS NULL
            AND requested_budget IS NULL AND requested_end_at IS NULL)
    ) IS TRUE);
