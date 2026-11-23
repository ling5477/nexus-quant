-- PostgreSQL 时间戳只保存微秒；额外保存原冻结窗口的纳秒余数，避免首次回读与幂等重放误报冲突。
-- 历史行的余数为零，准确保留已有时间，不推测历史上未保存的精度。常量默认值不重写全表。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE shadow_runs
    ADD COLUMN window_start_nanosecond_remainder smallint NOT NULL DEFAULT 0,
    ADD COLUMN window_end_nanosecond_remainder smallint NOT NULL DEFAULT 0;

COMMENT ON COLUMN shadow_runs.window_start_nanosecond_remainder IS
    '冻结窗口开始时刻在微秒时间戳之后的纳秒余数（0至999）；历史行保留已存微秒，余数为0。';
COMMENT ON COLUMN shadow_runs.window_end_nanosecond_remainder IS
    '冻结窗口结束时刻在微秒时间戳之后的纳秒余数（0至999）；与开始时刻共同参与完整窗口比较。';

ALTER TABLE shadow_runs
    ADD CONSTRAINT chk_shadow_runs_window_start_remainder CHECK (
        window_start_nanosecond_remainder BETWEEN 0 AND 999
        AND (window_start IS NOT NULL OR window_start_nanosecond_remainder = 0)
    ) NOT VALID,
    ADD CONSTRAINT chk_shadow_runs_window_end_remainder CHECK (
        window_end_nanosecond_remainder BETWEEN 0 AND 999
        AND (window_end IS NOT NULL OR window_end_nanosecond_remainder = 0)
    ) NOT VALID,
    DROP CONSTRAINT chk_shadow_runs_window,
    ADD CONSTRAINT chk_shadow_runs_window CHECK (
        window_start IS NULL OR window_end IS NULL
        OR (window_end, window_end_nanosecond_remainder)
           >= (window_start, window_start_nanosecond_remainder)
    ) NOT VALID;

ALTER TABLE shadow_runs VALIDATE CONSTRAINT chk_shadow_runs_window_start_remainder;
ALTER TABLE shadow_runs VALIDATE CONSTRAINT chk_shadow_runs_window_end_remainder;
ALTER TABLE shadow_runs VALIDATE CONSTRAINT chk_shadow_runs_window;
