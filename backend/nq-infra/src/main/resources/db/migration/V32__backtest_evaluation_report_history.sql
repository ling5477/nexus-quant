-- 每次明确评估保留独立报告身份，发布和Shadow继续引用原报告；不改写既有报告或历史回执。
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE backtest_eval_reports DROP CONSTRAINT uq_backtest_eval_reports_run;

CREATE INDEX idx_backtest_eval_reports_run_latest
    ON backtest_eval_reports (backtest_run_id, evaluated_at DESC NULLS LAST, created_at DESC, eval_report_id DESC);

COMMENT ON COLUMN backtest_eval_reports.backtest_run_id IS '所属冻结回测运行；多次明确评估追加独立报告，按运行读取时选择最新报告，不替换历史报告身份。';
COMMENT ON INDEX idx_backtest_eval_reports_run_latest IS '按冻结运行有界读取最新评估；历史报告仍可按原主键精确追溯。';
