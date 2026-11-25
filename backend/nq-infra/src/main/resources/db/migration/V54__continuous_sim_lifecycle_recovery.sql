SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE continuous_sim_runs
    ADD COLUMN overlap_check_pending boolean NOT NULL DEFAULT false,
    ADD COLUMN pending_revision_detected_at timestamptz,
    ADD CONSTRAINT ck_continuous_overlap_check CHECK (
        (NOT overlap_check_pending OR (end_at IS NOT NULL AND lifecycle_status <> 'COMPLETED'))
        AND (lifecycle_status IS DISTINCT FROM 'COMPLETED' OR revision_detected_at IS NULL)
        AND (pending_revision_detected_at IS NULL OR (overlap_check_pending
             AND revision_detected_at IS NULL AND isfinite(pending_revision_detected_at)))
    );

-- 既有终态保持原样；新写入的终态须遵循人工请求先于终止的因果顺序。
ALTER TABLE continuous_sim_runs ADD CONSTRAINT ck_continuous_manual_termination_time
    CHECK (status <> 'STOPPED' OR manual_end_at IS NULL OR updated_at >= manual_end_at) NOT VALID;

COMMENT ON COLUMN continuous_sim_runs.overlap_check_pending IS
    '有界行情请求的重叠核验尚未确认。进程中断后，缺少原响应的运行不得正常完成。';
COMMENT ON COLUMN continuous_sim_runs.pending_revision_detected_at IS
    '首次在原响应中发现重叠 K 线摘要不一致的时刻，供中断后原值转入正式修订阻断。';

-- 保留函数签名以兼容既有调用方。配合终止写入，在获得同一游标锁后采样。
CREATE OR REPLACE FUNCTION nq_request_continuous_end(target_run text, requested_at timestamptz)
RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE bound continuous_sim_runs%ROWTYPE;
DECLARE ended_request_at timestamptz;
BEGIN
    SELECT * INTO bound FROM continuous_sim_runs
        WHERE paper_run_id=target_run AND end_at IS NOT NULL FOR UPDATE;
    IF NOT FOUND THEN RETURN false; END IF;
    IF bound.status<>'STOPPED' AND bound.manual_end_at IS NULL THEN
        ended_request_at := clock_timestamp();
        UPDATE continuous_sim_runs SET manual_end_at=ended_request_at,
            lifecycle_status='ADMISSION_CLOSED',updated_at=ended_request_at WHERE paper_run_id=target_run;
    END IF;
    RETURN true;
END;
$$;
