-- V11__gate_f_schema_comments_backfill.sql
-- 目的：为 GateF 新增表补齐表注释与字段注释。
-- Why:
-- 1) GateF 已完成冻结，但 V7 ~ V10 仅落结构，未补 COMMENT ON TABLE / COLUMN；
-- 2) 当前仓库在 V5 / V6 已建立注释规范，GateF 表需要收口到同一可审查标准；
-- 3) 本批只回填注释，不改 schema、索引和业务语义。

COMMENT ON TABLE research_configs IS 'GateF 研究配置表。保存研究配置主身份、来源策略快照与数据集规格，不直接承载执行域运行事实。';
COMMENT ON COLUMN research_configs.research_config_id IS '研究配置主键。';
COMMENT ON COLUMN research_configs.source_strategy_id IS '来源执行域 strategy_definition 标识，只用于引用来源，不直接复用执行域主键语义。';
COMMENT ON COLUMN research_configs.name IS '研究配置展示名称。';
COMMENT ON COLUMN research_configs.description IS '研究配置描述信息。';
COMMENT ON COLUMN research_configs.strategy_snapshot IS '来源策略定义快照，JSONB 保存发布前的研究输入事实。';
COMMENT ON COLUMN research_configs.config_json IS '研究配置 JSON，包括参数 schema/defaults 与 datasetSpec。';
COMMENT ON COLUMN research_configs.created_at IS '研究配置创建时间。';
COMMENT ON COLUMN research_configs.updated_at IS '研究配置最后更新时间。';

COMMENT ON TABLE backtest_configs IS 'GateF 回测配置表。保存研究配置派生出的回测运行窗口、执行参数和评估参数占位。';
COMMENT ON COLUMN backtest_configs.backtest_config_id IS '回测配置主键。';
COMMENT ON COLUMN backtest_configs.research_config_id IS '所属研究配置 ID。';
COMMENT ON COLUMN backtest_configs.name IS '回测配置展示名称。';
COMMENT ON COLUMN backtest_configs.description IS '回测配置描述信息。';
COMMENT ON COLUMN backtest_configs.config_json IS '回测配置快照 JSON，包括时间窗口、初始资金和 executionSpec。';
COMMENT ON COLUMN backtest_configs.evaluation_spec_json IS '评估参数占位 JSON，供 GateF-4 读取。';
COMMENT ON COLUMN backtest_configs.created_at IS '回测配置创建时间。';
COMMENT ON COLUMN backtest_configs.updated_at IS '回测配置最后更新时间。';

COMMENT ON TABLE backtest_runs IS 'GateF 回测运行表。保存回测运行身份、状态、失败信息与 run 级执行摘要，不保存 sim_* 明细。';
COMMENT ON COLUMN backtest_runs.backtest_run_id IS '回测运行主键。';
COMMENT ON COLUMN backtest_runs.backtest_config_id IS '所属回测配置 ID。';
COMMENT ON COLUMN backtest_runs.research_config_id IS '所属研究配置 ID。';
COMMENT ON COLUMN backtest_runs.source_strategy_id IS '来源策略定义 ID。';
COMMENT ON COLUMN backtest_runs.status IS '回测运行状态，固定口径为 CREATED / PREPARING / RUNNING / SUCCEEDED / FAILED / CANCELLED。';
COMMENT ON COLUMN backtest_runs.strategy_snapshot IS '运行时引用的来源策略快照。';
COMMENT ON COLUMN backtest_runs.backtest_config_snapshot IS '运行时引用的回测配置快照。';
COMMENT ON COLUMN backtest_runs.summary_json IS 'run 级执行摘要 JSON。只保存统计摘要，不保存 sim_* 明细事实。';
COMMENT ON COLUMN backtest_runs.requested_at IS '回测运行请求创建时间。';
COMMENT ON COLUMN backtest_runs.started_at IS '显式 start run 后的执行开始时间。';
COMMENT ON COLUMN backtest_runs.finished_at IS '回测运行完成或失败时间。';
COMMENT ON COLUMN backtest_runs.failure_code IS '运行失败码。';
COMMENT ON COLUMN backtest_runs.failure_message IS '运行失败消息。';
COMMENT ON COLUMN backtest_runs.created_at IS '回测运行记录创建时间。';
COMMENT ON COLUMN backtest_runs.updated_at IS '回测运行记录最后更新时间。';

COMMENT ON TABLE sim_orders IS 'GateF 模拟订单事实表。保存回测执行过程中生成的最小模拟订单，不复用实盘 orders 表。';
COMMENT ON COLUMN sim_orders.sim_order_id IS '模拟订单主键。';
COMMENT ON COLUMN sim_orders.backtest_run_id IS '所属回测运行 ID。';
COMMENT ON COLUMN sim_orders.symbol IS '回测交易对标识。';
COMMENT ON COLUMN sim_orders.side IS '模拟订单方向。';
COMMENT ON COLUMN sim_orders.order_type IS '模拟订单类型。当前主要为 MARKET。';
COMMENT ON COLUMN sim_orders.requested_quantity IS '请求下单数量。';
COMMENT ON COLUMN sim_orders.requested_price IS '请求下单价格。当前 close 成交规则下记录 bar close。';
COMMENT ON COLUMN sim_orders.status IS '模拟订单状态，固定口径为 CREATED / FILLED / REJECTED。';
COMMENT ON COLUMN sim_orders.created_at IS '模拟订单创建时间。';
COMMENT ON COLUMN sim_orders.filled_at IS '模拟订单成交时间。';
COMMENT ON COLUMN sim_orders.reject_reason IS '模拟订单拒绝原因。';
COMMENT ON COLUMN sim_orders.updated_at IS '模拟订单最后更新时间。';

COMMENT ON TABLE sim_trades IS 'GateF 模拟成交事实表。保存由模拟订单撮合生成的成交结果，不复用实盘 trades 表。';
COMMENT ON COLUMN sim_trades.sim_trade_id IS '模拟成交主键。';
COMMENT ON COLUMN sim_trades.sim_order_id IS '所属模拟订单 ID。';
COMMENT ON COLUMN sim_trades.backtest_run_id IS '所属回测运行 ID。';
COMMENT ON COLUMN sim_trades.symbol IS '回测交易对标识。';
COMMENT ON COLUMN sim_trades.side IS '模拟成交方向。';
COMMENT ON COLUMN sim_trades.quantity IS '模拟成交数量。';
COMMENT ON COLUMN sim_trades.trade_price IS '模拟成交价格。当前统一按 bar close 成交。';
COMMENT ON COLUMN sim_trades.fee_amount IS '模拟成交手续费金额。';
COMMENT ON COLUMN sim_trades.slippage_amount IS '模拟成交滑点金额。';
COMMENT ON COLUMN sim_trades.traded_at IS '模拟成交发生时间。';
COMMENT ON COLUMN sim_trades.created_at IS '模拟成交记录创建时间。';
COMMENT ON COLUMN sim_trades.updated_at IS '模拟成交记录最后更新时间。';

COMMENT ON TABLE sim_positions IS 'GateF 模拟持仓事实表。保存 run + symbol 维度的当前持仓，不复用实盘 positions 投影。';
COMMENT ON COLUMN sim_positions.sim_position_id IS '模拟持仓主键。';
COMMENT ON COLUMN sim_positions.backtest_run_id IS '所属回测运行 ID。';
COMMENT ON COLUMN sim_positions.symbol IS '回测交易对标识。';
COMMENT ON COLUMN sim_positions.quantity IS '当前模拟持仓数量。';
COMMENT ON COLUMN sim_positions.average_entry_price IS '当前模拟持仓均价。';
COMMENT ON COLUMN sim_positions.realized_pnl IS '当前已实现 PnL。';
COMMENT ON COLUMN sim_positions.created_at IS '模拟持仓记录创建时间。';
COMMENT ON COLUMN sim_positions.updated_at IS '模拟持仓最后更新时间。';

COMMENT ON TABLE sim_pnl_snapshots IS 'GateF 模拟权益快照表。保存回测运行过程中逐时点的现金、权益与 PnL 原始序列。';
COMMENT ON COLUMN sim_pnl_snapshots.sim_pnl_snapshot_id IS '模拟 PnL 快照主键。';
COMMENT ON COLUMN sim_pnl_snapshots.backtest_run_id IS '所属回测运行 ID。';
COMMENT ON COLUMN sim_pnl_snapshots.snapshot_time IS '快照时间。当前通常与 bar close 或成交后时点一致。';
COMMENT ON COLUMN sim_pnl_snapshots.cash_balance IS '当前现金余额。';
COMMENT ON COLUMN sim_pnl_snapshots.position_market_value IS '当前持仓市值。';
COMMENT ON COLUMN sim_pnl_snapshots.realized_pnl IS '当前已实现 PnL。';
COMMENT ON COLUMN sim_pnl_snapshots.unrealized_pnl IS '当前未实现 PnL。';
COMMENT ON COLUMN sim_pnl_snapshots.total_fee IS '累计手续费。';
COMMENT ON COLUMN sim_pnl_snapshots.total_slippage IS '累计滑点。';
COMMENT ON COLUMN sim_pnl_snapshots.equity IS '当前总权益。';
COMMENT ON COLUMN sim_pnl_snapshots.net_pnl IS '当前净 PnL。';
COMMENT ON COLUMN sim_pnl_snapshots.created_at IS '模拟 PnL 快照记录创建时间。';

COMMENT ON TABLE backtest_eval_reports IS 'GateF 回测评估报告表。保存 run 级评估结果、关键指标与评估失败信息，不改写 sim_* 事实。';
COMMENT ON COLUMN backtest_eval_reports.eval_report_id IS '评估报告主键。';
COMMENT ON COLUMN backtest_eval_reports.backtest_run_id IS '所属回测运行 ID，唯一约束保证同一 run 仅保留一条评估报告。';
COMMENT ON COLUMN backtest_eval_reports.evaluation_status IS '评估状态，固定口径为 SUCCEEDED / FAILED。';
COMMENT ON COLUMN backtest_eval_reports.initial_capital IS '评估使用的初始资金。';
COMMENT ON COLUMN backtest_eval_reports.final_cash_balance IS '最终现金余额。';
COMMENT ON COLUMN backtest_eval_reports.final_position_market_value IS '最终持仓市值。';
COMMENT ON COLUMN backtest_eval_reports.final_equity IS '最终权益。';
COMMENT ON COLUMN backtest_eval_reports.realized_pnl IS '最终已实现 PnL。';
COMMENT ON COLUMN backtest_eval_reports.unrealized_pnl IS '最终未实现 PnL。';
COMMENT ON COLUMN backtest_eval_reports.net_pnl IS '净 PnL。';
COMMENT ON COLUMN backtest_eval_reports.total_return_rate IS '总收益率，固定口径为 netPnl / initialCapital。';
COMMENT ON COLUMN backtest_eval_reports.total_fee IS '累计手续费。';
COMMENT ON COLUMN backtest_eval_reports.total_slippage IS '累计滑点。';
COMMENT ON COLUMN backtest_eval_reports.order_count IS '模拟订单数量。';
COMMENT ON COLUMN backtest_eval_reports.trade_count IS '模拟成交数量。';
COMMENT ON COLUMN backtest_eval_reports.winning_trade_count IS '盈利闭合交易数量。';
COMMENT ON COLUMN backtest_eval_reports.losing_trade_count IS '亏损闭合交易数量。';
COMMENT ON COLUMN backtest_eval_reports.flat_trade_count IS '盈亏为零的闭合交易数量。';
COMMENT ON COLUMN backtest_eval_reports.win_rate IS '胜率。';
COMMENT ON COLUMN backtest_eval_reports.max_drawdown IS '最大回撤。';
COMMENT ON COLUMN backtest_eval_reports.max_drawdown_rate IS '最大回撤率。';
COMMENT ON COLUMN backtest_eval_reports.sharpe_ratio IS '非年化 SharpeRatio。';
COMMENT ON COLUMN backtest_eval_reports.report_json IS '评估明细 JSON。';
COMMENT ON COLUMN backtest_eval_reports.failure_code IS '评估失败码。';
COMMENT ON COLUMN backtest_eval_reports.failure_message IS '评估失败消息。';
COMMENT ON COLUMN backtest_eval_reports.evaluated_at IS '评估执行时间。';
COMMENT ON COLUMN backtest_eval_reports.created_at IS '评估报告创建时间。';
COMMENT ON COLUMN backtest_eval_reports.updated_at IS '评估报告最后更新时间。';

COMMENT ON TABLE backtest_publish_records IS 'GateF 研究产物发布记录表。保存回测运行到执行域 strategy_definition 的发布事实、来源快照与失败信息。';
COMMENT ON COLUMN backtest_publish_records.publish_record_id IS '发布记录主键。';
COMMENT ON COLUMN backtest_publish_records.backtest_run_id IS '所属回测运行 ID，唯一约束保证同一 run 仅保留一条发布事实。';
COMMENT ON COLUMN backtest_publish_records.research_config_id IS '来源研究配置 ID。';
COMMENT ON COLUMN backtest_publish_records.backtest_config_id IS '来源回测配置 ID。';
COMMENT ON COLUMN backtest_publish_records.source_strategy_id IS '来源执行域 strategy_definition ID。';
COMMENT ON COLUMN backtest_publish_records.eval_report_id IS '关联评估报告 ID。';
COMMENT ON COLUMN backtest_publish_records.target_strategy_definition_id IS '发布后生成的执行域 strategy_definition ID。';
COMMENT ON COLUMN backtest_publish_records.publish_status IS '发布状态，固定口径为 SUCCEEDED / FAILED。';
COMMENT ON COLUMN backtest_publish_records.publish_name IS '发布展示名。';
COMMENT ON COLUMN backtest_publish_records.publish_snapshot_json IS '发布映射快照 JSON。';
COMMENT ON COLUMN backtest_publish_records.evaluation_summary_json IS '发布时固化的评估摘要 JSON。';
COMMENT ON COLUMN backtest_publish_records.failure_code IS '发布失败码。';
COMMENT ON COLUMN backtest_publish_records.failure_message IS '发布失败消息。';
COMMENT ON COLUMN backtest_publish_records.published_at IS '发布完成时间。';
COMMENT ON COLUMN backtest_publish_records.created_at IS '发布记录创建时间。';
COMMENT ON COLUMN backtest_publish_records.updated_at IS '发布记录最后更新时间。';
