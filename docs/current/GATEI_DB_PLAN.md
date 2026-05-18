# GateI DB Plan

本文件只规划 GateI DB 结构，不新增 migration，不修改业务表结构。数据库事实仍以 Flyway migrations 为准。

## 通用 DB 约定

- 后续所有新增表必须包含 PostgreSQL `COMMENT ON TABLE`。
- 后续所有新增字段必须包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段必须在注释中说明允许值。
- JSONB 快照字段必须说明用途、结构边界和禁止保存敏感信息。
- 审计字段默认包含 `created_by`、`created_at`、`updated_at`；状态流转类表补充操作人和时间。
- 幂等优先通过业务唯一约束和 `idempotency_key` 组合保证。

## `strategy_versions`

用途：记录策略的可回测、可发布版本。

字段草案：

- `version_id`
- `strategy_id`
- `version_name`
- `status`
- `description`
- `parameter_snapshot_json`
- `source_snapshot_json`
- `created_by`
- `created_at`
- `updated_at`
- `frozen_at`
- `archived_at`

唯一约束：

- `strategy_id + version_name`

索引：

- `strategy_id + status + created_at`
- `status + frozen_at`

JSONB 快照字段用途：

- `parameter_snapshot_json` 保存策略参数快照。
- `source_snapshot_json` 保存策略定义、实现引用或校验摘要，不保存密钥。

幂等策略：

- 同 strategy、versionName、参数快照重复创建时返回已有版本或冲突。

## `strategy_publish_versions` 或 `publish_records` 增强

用途：记录策略版本进入 SIM / Paper 的发布事实。

字段草案：

- `publish_version_id`
- `strategy_version_id`
- `evaluation_report_id`
- `target_environment`
- `status`
- `approval_note`
- `created_by`
- `created_at`
- `approved_by`
- `approved_at`
- `published_at`
- `snapshot_json`

唯一约束：

- `strategy_version_id + evaluation_report_id + target_environment`

索引：

- `strategy_version_id + status`
- `target_environment + status + created_at`

JSONB 快照字段用途：

- `snapshot_json` 固化策略版本、参数、评估摘要和发布审批信息。

幂等策略：

- 相同策略版本、评估报告和目标环境重复发布请求必须幂等。

## `backtest_configs` 增强

用途：把回测配置从 dataset 绑定扩展到 dataset、策略版本和参数快照共同绑定。

字段草案：

- `strategy_version_id`
- `strategy_version_snapshot_json`
- `parameter_snapshot_json`
- `config_status`
- `updated_by`
- `updated_at`

唯一约束：

- 可选：`strategy_version_id + dataset_id + parameter_hash`

索引：

- `strategy_version_id`
- `dataset_id + strategy_version_id`
- `config_status + updated_at`

JSONB 快照字段用途：

- `strategy_version_snapshot_json` 保存绑定时策略版本的摘要。
- `parameter_snapshot_json` 保存回测参数，保证 run 可复现。

## `backtest_runs` 结果追溯增强

用途：保证单次 backtest run 可追溯到 config、dataset、strategy version 和参数快照。

字段草案：

- `strategy_version_id`
- `strategy_version_snapshot_json`
- `parameter_snapshot_json`
- `input_snapshot_json`
- `result_summary_json`

唯一约束：

- 可选：`config_id + input_hash + created_at` 不建议强制唯一，避免阻断重复实验。

索引：

- `strategy_version_id + created_at`
- `dataset_id + created_at`
- `status + created_at`

JSONB 快照字段用途：

- `input_snapshot_json` 保存 config、dataset、strategy version 和参数的完整输入摘要。
- `result_summary_json` 保存核心结果摘要，不替代评估报告表。

## `backtest_eval_reports` 指标增强

用途：记录回测评估报告和核心指标。

字段草案：

- `report_id`
- `backtest_run_id`
- `strategy_version_id`
- `dataset_id`
- `total_return`
- `annualized_return`
- `max_drawdown`
- `win_rate`
- `profit_loss_ratio`
- `trade_count`
- `metrics_json`
- `created_by`
- `created_at`

唯一约束：

- `backtest_run_id`

索引：

- `strategy_version_id + created_at`
- `dataset_id + created_at`
- `total_return`
- `max_drawdown`

JSONB 快照字段用途：

- `metrics_json` 保存扩展指标、统计口径和计算版本。

## `paper_trading_runs`

用途：记录 SIM / Paper Trading 运行实例。

字段草案：

- `run_id`
- `publish_version_id`
- `account_id`
- `environment`
- `status`
- `initial_capital`
- `current_equity`
- `risk_profile_id`
- `started_at`
- `stopped_at`
- `stop_reason`
- `created_by`
- `created_at`
- `updated_at`
- `run_snapshot_json`
- `idempotency_key`

唯一约束：

- `idempotency_key`

索引：

- `publish_version_id + status`
- `account_id + environment + status`
- `status + updated_at`

JSONB 快照字段用途：

- `run_snapshot_json` 固化发布版本、账户上下文、风险配置和初始资金。

## `paper_trading_orders`

用途：记录 Paper run 产生的订单事实。

字段草案：

- `paper_order_id`
- `run_id`
- `client_order_id`
- `symbol`
- `side`
- `order_type`
- `price`
- `quantity`
- `filled_quantity`
- `status`
- `risk_result_id`
- `order_snapshot_json`
- `created_at`
- `updated_at`

唯一约束：

- `run_id + client_order_id`

索引：

- `run_id + created_at`
- `run_id + symbol + status`

JSONB 快照字段用途：

- `order_snapshot_json` 保存下单时信号、风控摘要和订单请求快照。

## `paper_trading_trades`

用途：记录 Paper run 的成交事实。

字段草案：

- `paper_trade_id`
- `run_id`
- `paper_order_id`
- `symbol`
- `side`
- `price`
- `quantity`
- `fee`
- `trade_time`
- `trade_snapshot_json`
- `created_at`

唯一约束：

- `run_id + paper_trade_id`

索引：

- `run_id + trade_time`
- `paper_order_id`
- `symbol + trade_time`

JSONB 快照字段用途：

- `trade_snapshot_json` 保存成交计算输入、手续费口径和撮合来源。

## `risk_check_results`

用途：记录风控检查结果并回写到 run/order/trade 维度。

字段草案：

- `risk_result_id`
- `run_id`
- `order_id`
- `risk_rule_code`
- `status`
- `reason`
- `input_snapshot_json`
- `result_snapshot_json`
- `checked_at`
- `created_at`

唯一约束：

- 可选：`run_id + order_id + risk_rule_code`

索引：

- `run_id + checked_at`
- `order_id`
- `status + checked_at`

JSONB 快照字段用途：

- `input_snapshot_json` 保存风控输入。
- `result_snapshot_json` 保存风控输出和拒绝原因。

## `equity_curve_snapshots`

用途：记录 Paper run 的资金曲线。

字段草案：

- `snapshot_id`
- `run_id`
- `snapshot_time`
- `equity`
- `cash_balance`
- `position_value`
- `realized_pnl`
- `unrealized_pnl`
- `drawdown`
- `source`
- `created_at`

唯一约束：

- `run_id + snapshot_time`

索引：

- `run_id + snapshot_time`

幂等策略：

- 同 run 和 snapshot time 重复写入时按确定计算结果 upsert。

## `position_curve_snapshots`

用途：记录 Paper run 的持仓曲线。

字段草案：

- `snapshot_id`
- `run_id`
- `symbol`
- `snapshot_time`
- `quantity`
- `average_price`
- `market_price`
- `market_value`
- `unrealized_pnl`
- `source`
- `created_at`

唯一约束：

- `run_id + symbol + snapshot_time`

索引：

- `run_id + symbol + snapshot_time`

幂等策略：

- 同 run、symbol、snapshot time 重复写入时按确定计算结果 upsert。

## `trade_replay_records`

用途：保存单次交易复盘所需的聚合快照。

字段草案：

- `replay_id`
- `run_id`
- `trade_id`
- `signal_snapshot_json`
- `risk_result_json`
- `order_snapshot_json`
- `trade_snapshot_json`
- `position_before_json`
- `position_after_json`
- `equity_before_json`
- `equity_after_json`
- `created_at`

唯一约束：

- `run_id + trade_id`

索引：

- `run_id + created_at`
- `trade_id`

JSONB 快照字段用途：

- 所有 JSONB 字段只保存复盘所需业务快照，不保存密钥、token、cookie。

## `emergency_stop_events`

用途：记录异常停机事件、触发原因和解除记录。

字段草案：

- `event_id`
- `scope_type`
- `scope_id`
- `status`
- `reason_code`
- `reason_detail`
- `triggered_by`
- `triggered_at`
- `resolved_by`
- `resolved_at`
- `resolution_note`
- `event_snapshot_json`
- `idempotency_key`

唯一约束：

- `idempotency_key`
- 可选：同一 `scope_type + scope_id` 同时只允许一个 active stop。

索引：

- `scope_type + scope_id + status`
- `status + triggered_at`

JSONB 快照字段用途：

- `event_snapshot_json` 保存触发时 run、账户、发布版本和风控摘要。

## 本轮限制

- 本轮不新增 migration。
- 本轮不修改业务表结构。
- 本轮不写 SQL 实现。
- 本轮不接 AI。
