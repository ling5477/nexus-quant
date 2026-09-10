-- 逻辑计划窗口直接存于 StrategyRun；旧行不猜测回填，新行由唯一键决定认领结果。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE strategy_runs
    ADD COLUMN admission_schedule_id VARCHAR(128),
    ADD COLUMN admission_due_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_strategy_run_admission_schedule FOREIGN KEY (admission_schedule_id)
        REFERENCES strategy_schedules(schedule_job_id),
    ADD CONSTRAINT chk_strategy_run_admission_pair CHECK (
        (admission_schedule_id IS NULL AND admission_due_at IS NULL)
        OR (admission_schedule_id IS NOT NULL AND admission_due_at IS NOT NULL AND trigger_type = 'SCHEDULER'));

CREATE UNIQUE INDEX uq_strategy_run_window_admission
    ON strategy_runs (strategy_id, account_id, admission_schedule_id, admission_due_at)
    WHERE admission_schedule_id IS NOT NULL;

COMMENT ON COLUMN strategy_runs.admission_schedule_id IS '原子认领的计划配置身份；历史和独立手动触发保持NULL，不自动回填。';
COMMENT ON COLUMN strategy_runs.admission_due_at IS 'CRON计算的逻辑到期时刻；不是扫描调用时刻或进程身份，消费后不得更换。';
COMMENT ON INDEX uq_strategy_run_window_admission IS '同策略、账户、计划和逻辑窗口至多一个StrategyRun；终结和恢复不释放此身份。';

CREATE FUNCTION nq_preserve_strategy_admission_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.admission_schedule_id IS NOT NULL AND
       (NEW.admission_schedule_id IS DISTINCT FROM OLD.admission_schedule_id
        OR NEW.admission_due_at IS DISTINCT FROM OLD.admission_due_at
        OR NEW.strategy_id IS DISTINCT FROM OLD.strategy_id
        OR NEW.account_id IS DISTINCT FROM OLD.account_id) THEN
        RAISE EXCEPTION 'strategy admission identity is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_preserve_strategy_admission_identity BEFORE UPDATE ON strategy_runs
    FOR EACH ROW EXECUTE FUNCTION nq_preserve_strategy_admission_identity();
