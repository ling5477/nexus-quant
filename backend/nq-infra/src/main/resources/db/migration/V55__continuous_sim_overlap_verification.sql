SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE continuous_sim_overlap_checks (
    check_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paper_run_id varchar(64) NOT NULL REFERENCES continuous_sim_runs(paper_run_id),
    first_open_time timestamptz NOT NULL,
    last_open_time timestamptz NOT NULL,
    retry boolean NOT NULL,
    started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at timestamptz,
    result varchar(16),
    failure_reason varchar(48),
    CHECK (isfinite(first_open_time) AND last_open_time=first_open_time+interval '1 hour'),
    CHECK ((finished_at IS NULL AND result IS NULL AND failure_reason IS NULL)
        OR (finished_at IS NOT NULL AND result IS NOT NULL AND (
            (result='VERIFIED' AND failure_reason IS NULL)
            OR (result='INCOMPLETE' AND failure_reason IS NOT NULL AND
                failure_reason IN ('INCOMPLETE_HTTP_RESPONSE','OVERLAP_CHECK_INCOMPLETE'))
            OR (result='REVISED' AND failure_reason IS NOT NULL AND
                failure_reason IN ('DATA_REVISION_DETECTED','INCOMPLETE_HTTP_RESPONSE','OVERLAP_CHECK_INCOMPLETE')))))
);
CREATE INDEX idx_continuous_sim_overlap_checks_run ON continuous_sim_overlap_checks (paper_run_id,check_id DESC);
ALTER TABLE continuous_sim_overlap_checks ENABLE ROW LEVEL SECURITY;
ALTER TABLE continuous_sim_overlap_checks FORCE ROW LEVEL SECURITY;
CREATE POLICY continuous_sim_overlap_checks_tenant ON continuous_sim_overlap_checks
    USING (EXISTS (SELECT 1 FROM continuous_sim_runs c WHERE c.paper_run_id=continuous_sim_overlap_checks.paper_run_id))
    WITH CHECK (EXISTS (SELECT 1 FROM continuous_sim_runs c WHERE c.paper_run_id=continuous_sim_overlap_checks.paper_run_id));
COMMENT ON TABLE continuous_sim_overlap_checks IS '连续 SIM 普通行情重叠核验的独立尝试。失败或中断保留原身份，重试使用新记录。';

CREATE FUNCTION nq_guard_continuous_overlap_check() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR OLD.finished_at IS NOT NULL OR NEW.finished_at IS NULL
        OR (OLD.check_id, OLD.paper_run_id, OLD.first_open_time, OLD.last_open_time, OLD.retry, OLD.started_at)
           IS DISTINCT FROM
           (NEW.check_id, NEW.paper_run_id, NEW.first_open_time, NEW.last_open_time, NEW.retry, NEW.started_at) THEN
        RAISE EXCEPTION 'CONTINUOUS_SIM_OVERLAP_CHECK_IMMUTABLE' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_continuous_sim_overlap_check_immutable BEFORE UPDATE OR DELETE ON continuous_sim_overlap_checks
    FOR EACH ROW EXECUTE FUNCTION nq_guard_continuous_overlap_check();
